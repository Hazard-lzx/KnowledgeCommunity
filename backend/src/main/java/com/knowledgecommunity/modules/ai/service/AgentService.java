package com.knowledgecommunity.modules.ai.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.modules.ai.agent.memory.AgentMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 服务：ReAct 循环核心
 *
 * 使用 Spring AI 1.0.0 的 ChatModel + ToolCallingManager 实现 ReAct 循环：
 * 发送消息 → 解析响应 → 若有工具调用则执行工具 → 将结果追加到历史 → 继续循环
 * 通过 PrintWriter 直接写入 SSE 事件，每次写入后立即 flush
 */
@Slf4j
@Service
public class AgentService {

    private final ChatModel chatModel;
    private final AgentMemoryService agentMemoryService;
    private final ObjectMapper objectMapper;
    private final List<ToolCallback> toolCallbacks;
    private final ToolCallingManager toolCallingManager;

    private static final int MAX_ITERATIONS = 15;

    private static final String SYSTEM_PROMPT = """
            你是一个智能创作Agent，目标是根据用户需求完成文章创作。
            你可以使用以下工具：
            - generateOutline: 根据标题生成结构化大纲
            - continueWrite: 根据上文续写内容
            - polishText: 润色文本，优化表达
            - generateSummary: 生成文章摘要
            - recommendTags: 推荐文章标签
            - publishArticle: 发布文章到社区

            请按需逐步调用工具，每次只调用一个工具，等待结果后再决定下一步。
            完成全部创作后，输出 FINAL_ANSWER，并附上完整的文章内容（Markdown格式）。
            不要在 FINAL_ANSWER 之前输出完整文章。
            """;

    public AgentService(ChatModel chatModel, AgentMemoryService agentMemoryService,
                        ObjectMapper objectMapper, List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.agentMemoryService = agentMemoryService;
        this.objectMapper = objectMapper;
        this.toolCallbacks = toolCallbacks;
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    /**
     * 执行 Agent 创作任务
     */
    public void execute(String goal, String style, Integer wordCount,
                        String sessionId, PrintWriter writer) {
        try {
            // 1. 构建系统提示词
            SystemMessage systemMessage = new SystemMessage(SYSTEM_PROMPT);

            // 2. 构建用户消息
            StringBuilder userMsg = new StringBuilder("创作目标：" + goal);
            if (style != null && !style.isBlank()) {
                userMsg.append("\n风格要求：").append(style);
            }
            if (wordCount != null && wordCount > 0) {
                userMsg.append("\n目标字数：约").append(wordCount).append("字");
            }
            UserMessage userMessage = new UserMessage(userMsg.toString());

            // 3. 初始化对话历史
            List<Message> conversationHistory = new ArrayList<>();
            conversationHistory.add(systemMessage);
            conversationHistory.add(userMessage);

            // 4. 构建 ToolCallingChatOptions，设置 toolCallbacks 并禁用内部自动执行
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();

            // 推送开始事件
            sendSse(writer, "thinking", "开始分析创作目标...");

            // 5. ReAct 循环
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                log.info("Agent ReAct 循环第 {} 轮, sessionId={}", i + 1, sessionId);

                // a. 调用 ChatModel
                Prompt prompt = new Prompt(conversationHistory, options);
                ChatResponse chatResponse = chatModel.call(prompt);

                if (chatResponse == null || chatResponse.getResult() == null) {
                    log.warn("ChatResponse 为空，终止循环, sessionId={}", sessionId);
                    sendSse(writer, "final_chunk", "模型未返回有效响应，请重试。");
                    sendSse(writer, "done", "");
                    return;
                }

                AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
                if (assistantMessage == null) {
                    log.warn("AssistantMessage 为空，终止循环, sessionId={}", sessionId);
                    sendSse(writer, "final_chunk", "模型未返回有效响应，请重试。");
                    sendSse(writer, "done", "");
                    return;
                }

                // 将 assistant 消息追加到历史
                conversationHistory.add(assistantMessage);

                // b. 检查是否有工具调用
                boolean hasToolCalls = assistantMessage.hasToolCalls();

                if (hasToolCalls) {
                    // 发送工具调用开始事件
                    String toolNames = assistantMessage.getToolCalls().stream()
                            .map(tc -> tc.name())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("unknown");
                    sendSse(writer, "tool_start", "调用工具：" + toolNames);
                    log.info("工具调用: {}, sessionId={}", toolNames, sessionId);

                    // 执行工具调用
                    ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

                    // 将工具响应消息追加到历史
                    List<Message> toolResponseMessages = toolResult.conversationHistory();
                    for (Message toolMsg : toolResponseMessages) {
                        conversationHistory.add(toolMsg);
                        String text = toolMsg.getText();
                        if (text != null && !text.isBlank()) {
                            sendSse(writer, "tool_result", text);
                            log.info("工具结果: 长度={}, sessionId={}", text.length(), sessionId);
                        }
                    }

                    // 发送思考事件
                    sendSse(writer, "thinking", "分析工具结果，规划下一步...");
                } else {
                    // c. 没有工具调用，视为最终回答
                    log.info("Agent 返回文本响应（无工具调用），视为最终回答, sessionId={}", sessionId);

                    String finalContent = assistantMessage.getText();
                    if (finalContent == null) {
                        finalContent = "创作完成，但未生成有效内容";
                    } else if (finalContent.contains("FINAL_ANSWER")) {
                        int idx = finalContent.indexOf("FINAL_ANSWER");
                        finalContent = finalContent.substring(idx + "FINAL_ANSWER".length()).trim();
                        if (finalContent.startsWith(":") || finalContent.startsWith("：")) {
                            finalContent = finalContent.substring(1).trim();
                        }
                    }

                    sendSse(writer, "final_chunk", finalContent);
                    sendSse(writer, "done", "");
                    return;
                }
            }

            // 超过最大迭代次数
            log.warn("Agent 达到最大迭代次数 MAX_ITERATIONS={}, sessionId={}", MAX_ITERATIONS, sessionId);
            sendSse(writer, "final_chunk", "已达到最大迭代次数，创作终止。");
            sendSse(writer, "done", "");

        } catch (Exception e) {
            log.error("Agent执行异常, sessionId={}", sessionId, e);
            sendSse(writer, "error", "执行异常：" + e.getMessage());
            sendSse(writer, "done", "");
        }
    }

    /**
     * 直接写入 SSE 事件到 PrintWriter，立即 flush
     * 格式：data: {"type":"xxx","data":"yyy"}\n\n
     */
    private void sendSse(PrintWriter writer, String type, String data) {
        try {
            String json = objectMapper.writeValueAsString(new AgentEvent(type, data));
            writer.write("data: " + json + "\n\n");
            writer.flush();
            log.info("SSE事件已发送: type={}, dataLength={}", type, data != null ? data.length() : 0);
        } catch (Exception e) {
            log.error("SSE事件发送失败: type={}", type, e);
        }
    }

    /** Agent 事件 DTO */
    public record AgentEvent(String type, String data) {}
}