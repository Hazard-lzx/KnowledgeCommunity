package com.knowledgecommunity.modules.ai.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.modules.ai.agent.memory.AgentMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Agent 服务：ReAct 循环核心
 *
 * 手动实现 ReAct 循环：发送消息 → 解析响应 → 若有工具调用则执行工具 → 将结果追加到历史 → 继续循环
 * 直接 HTTP 调用 DeepSeek API，完全手动控制
 * 通过 PrintWriter 直接写入 SSE 事件，每次写入后立即 flush
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMemoryService memoryService;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.chat-model}")
    private String chatModel;

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

    private static final int MAX_ITERATIONS = 15;

    /** 工具定义（发送给模型的 function 声明） */
    private static final List<Map<String, Object>> TOOL_DEFINITIONS = List.of(
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "generateOutline",
                            "description", "根据文章标题生成一份结构化大纲，返回Markdown列表格式",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "title", Map.of("type", "string", "description", "文章标题")
                                    ),
                                    "required", List.of("title")
                            )
                    )
            ),
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "continueWrite",
                            "description", "根据上文内容续写文章，返回续写的文本",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "context", Map.of("type", "string", "description", "上文内容"),
                                            "wordCount", Map.of("type", "integer", "description", "续写目标字数")
                                    ),
                                    "required", List.of("context")
                            )
                    )
            ),
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "polishText",
                            "description", "润色文本，优化表达和文风",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "text", Map.of("type", "string", "description", "需要润色的文本"),
                                            "style", Map.of("type", "string", "description", "风格要求，如：轻松易读、专业严谨")
                                    ),
                                    "required", List.of("text")
                            )
                    )
            ),
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "generateSummary",
                            "description", "生成文章摘要，约200字",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "content", Map.of("type", "string", "description", "文章全文")
                                    ),
                                    "required", List.of("content")
                            )
                    )
            ),
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "recommendTags",
                            "description", "根据文章内容推荐标签，返回逗号分隔的标签列表",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "content", Map.of("type", "string", "description", "文章全文")
                                    ),
                                    "required", List.of("content")
                            )
                    )
            ),
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "publishArticle",
                            "description", "发布文章到社区，调用此工具表示创作完成",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "title", Map.of("type", "string", "description", "文章标题"),
                                            "content", Map.of("type", "string", "description", "文章内容（Markdown格式）"),
                                            "summary", Map.of("type", "string", "description", "文章摘要"),
                                            "tags", Map.of("type", "string", "description", "标签，逗号分隔")
                                    ),
                                    "required", List.of("title", "content")
                            )
                    )
            )
    );

    /**
     * 执行 Agent 创作任务
     */
    public void execute(String goal, String style, Integer wordCount,
                        String sessionId, PrintWriter writer) {
        try {
            // 构建用户消息
            StringBuilder userMsg = new StringBuilder("创作目标：" + goal);
            if (style != null && !style.isBlank()) {
                userMsg.append("\n风格要求：").append(style);
            }
            if (wordCount != null && wordCount > 0) {
                userMsg.append("\n目标字数：约").append(wordCount).append("字");
            }

            // 初始化消息列表（OpenAI 格式）
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", SYSTEM_PROMPT);
            messages.add(sysMsg);
            Map<String, Object> userMsgMap = new LinkedHashMap<>();
            userMsgMap.put("role", "user");
            userMsgMap.put("content", userMsg.toString());
            messages.add(userMsgMap);

            // 推送开始事件
            sendSse(writer, "thinking", "开始分析创作目标...");

            // ReAct 循环
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                log.info("Agent ReAct 循环第 {} 轮, sessionId={}", i + 1, sessionId);

                // 调用 DeepSeek Chat API
                JsonNode responseNode = callChatApi(messages);
                JsonNode choice = responseNode.get("choices").get(0);
                JsonNode messageNode = choice.get("message");

                // 提取 assistant 文本内容
                String assistantContent = "";
                if (messageNode.has("content") && !messageNode.get("content").isNull()) {
                    assistantContent = messageNode.get("content").asText();
                }

                // 将 assistant 消息追加到历史
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", assistantContent);
                if (messageNode.has("tool_calls") && !messageNode.get("tool_calls").isNull() && messageNode.get("tool_calls").size() > 0) {
                    assistantMsg.put("tool_calls", objectMapper.convertValue(messageNode.get("tool_calls"), List.class));
                }
                messages.add(assistantMsg);

                // 检查是否有工具调用
                JsonNode toolCallsNode = messageNode.get("tool_calls");
                boolean hasToolCalls = toolCallsNode != null && !toolCallsNode.isNull() && toolCallsNode.size() > 0;

                if (hasToolCalls) {
                    boolean publishCalled = false;
                    for (JsonNode toolCall : toolCallsNode) {
                        String toolCallId = toolCall.get("id").asText();
                        String toolName = toolCall.get("function").get("name").asText();
                        String toolArgs = toolCall.get("function").get("arguments").asText();

                        sendSse(writer, "tool_start", "调用工具：" + toolName);
                        log.info("工具调用: {} args={}", toolName, toolArgs);

                        // 执行工具
                        String toolResult = executeTool(toolName, toolArgs);

                        sendSse(writer, "tool_result", toolResult != null ? toolResult : "无结果");
                        log.info("工具结果: {} (长度={})", toolName, toolResult != null ? toolResult.length() : 0);

                        // 将工具结果追加到消息历史
                        Map<String, Object> toolMsg = new LinkedHashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", toolCallId);
                        toolMsg.put("content", toolResult != null ? toolResult : "");
                        messages.add(toolMsg);

                        if ("publishArticle".equals(toolName)) {
                            publishCalled = true;
                        }
                    }

                    // 如果调用了 publishArticle，视为创作完成
                    if (publishCalled) {
                        log.info("publishArticle 已调用，发送最终结果, sessionId={}", sessionId);
                        // 优先从 publishArticle 参数中提取完整文章内容
                        String publishContent = extractPublishContent(toolCallsNode);
                        String finalContent = publishContent != null ? publishContent : collectArticleContent(messages);
                        sendSse(writer, "final_chunk", finalContent);
                        sendSse(writer, "done", "");
                        return;
                    }
                    sendSse(writer, "thinking", "分析工具结果，规划下一步...");
                } else {
                    // 没有工具调用，视为最终回答
                    log.info("Agent 返回文本响应（无工具调用），视为最终回答, sessionId={}", sessionId);
                    String finalContent = assistantContent;
                    if (finalContent.contains("FINAL_ANSWER")) {
                        int idx = finalContent.indexOf("FINAL_ANSWER");
                        finalContent = finalContent.substring(idx + "FINAL_ANSWER".length()).trim();
                        if (finalContent.startsWith(":") || finalContent.startsWith("：")) {
                            finalContent = finalContent.substring(1).trim();
                        }
                    }
                    if (finalContent.isBlank()) {
                        finalContent = collectArticleContent(messages);
                        if (finalContent.isBlank()) {
                            finalContent = "创作完成，但未生成有效内容";
                        }
                    }
                    sendSse(writer, "final_chunk", finalContent);
                    sendSse(writer, "done", "");
                    return;
                }
            }

            // 超过最大迭代次数
            String finalContent = collectArticleContent(messages);
            sendSse(writer, "final_chunk", finalContent.isBlank() ? "已达到最大迭代次数，创作终止。" : finalContent);
            sendSse(writer, "done", "");

        } catch (Exception e) {
            log.error("Agent执行异常, sessionId={}", sessionId, e);
            sendSse(writer, "error", "执行异常：" + e.getMessage());
            sendSse(writer, "done", "");
        }
    }

    /** 调用 DeepSeek Chat API（非流式，带超时保护） */
    private JsonNode callChatApi(List<Map<String, Object>> messages) throws Exception {
        String url = baseUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", messages);
        body.put("tools", TOOL_DEFINITIONS);
        body.put("temperature", 0.7);

        String jsonBody = objectMapper.writeValueAsString(body);
        log.info("调用DeepSeek API, messages数量={}", messages.size());

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return postJson(url, jsonBody);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        String response;
        try {
            response = future.get(90, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("API调用超时（90秒），请稍后重试");
        }

        JsonNode result = objectMapper.readTree(response);
        log.info("DeepSeek API 响应: finish_reason={}",
                result.get("choices").get(0).has("finish_reason")
                        ? result.get("choices").get(0).get("finish_reason").asText() : "unknown");
        return result;
    }

    /** 执行工具调用 */
    private String executeTool(String toolName, String arguments) {
        try {
            // publishArticle 不需要解析参数，直接返回成功
            if ("publishArticle".equals(toolName)) {
                return "文章已准备好发布。";
            }

            JsonNode args = objectMapper.readTree(arguments);

            return switch (toolName) {
                case "generateOutline" -> generateOutline(args.get("title").asText());
                case "continueWrite" -> continueWrite(
                        args.get("context").asText(),
                        args.has("wordCount") ? args.get("wordCount").asInt() : 500);
                case "polishText" -> polishText(
                        args.get("text").asText(),
                        args.has("style") ? args.get("style").asText() : "轻松易读");
                case "generateSummary" -> generateSummary(args.get("content").asText());
                case "recommendTags" -> recommendTags(args.get("content").asText());
                default -> "未找到工具：" + toolName;
            };
        } catch (Exception e) {
            log.error("工具执行异常: toolName={}", toolName, e);
            return "工具执行失败：" + e.getMessage();
        }
    }

    /** 生成大纲 */
    private String generateOutline(String title) throws Exception {
        String prompt = "请为文章《" + title + "》生成一份结构化大纲，返回Markdown列表格式，包含至少3个二级标题，每个二级标题下包含2-3个要点。只返回大纲内容，不要其他说明。";
        return callSimpleChat(prompt);
    }

    /** 续写 */
    private String continueWrite(String context, int wordCount) throws Exception {
        String prompt = "请根据以下上文续写文章，续写约" + wordCount + "字，保持风格一致，只返回续写内容：\n\n" + context;
        return callSimpleChat(prompt);
    }

    /** 润色 */
    private String polishText(String text, String style) throws Exception {
        String prompt = "请以「" + style + "」的风格润色以下文本，只返回润色后的内容：\n\n" + text;
        return callSimpleChat(prompt);
    }

    /** 生成摘要 */
    private String generateSummary(String content) throws Exception {
        String prompt = "请为以下文章生成约200字的摘要，只返回摘要内容：\n\n" + content;
        return callSimpleChat(prompt);
    }

    /** 推荐标签 */
    private String recommendTags(String content) throws Exception {
        String prompt = "请根据以下文章内容推荐3-5个标签，以逗号分隔返回，只返回标签：\n\n" + content;
        return callSimpleChat(prompt);
    }

    /** 简单 Chat 调用（无工具） */
    private String callSimpleChat(String prompt) throws Exception {
        String url = baseUrl + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", 0.7);

        String response = postJson(url, objectMapper.writeValueAsString(body));
        JsonNode node = objectMapper.readTree(response);
        return node.get("choices").get(0).get("message").get("content").asText();
    }

    /** 收集消息历史中的文章内容（大纲+续写+润色结果） */
    private String collectArticleContent(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : messages) {
            if ("tool".equals(msg.get("role"))) {
                String content = (String) msg.get("content");
                if (content != null && !content.isBlank() && !content.contains("文章已准备好发布")) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(content);
                }
            }
        }
        return sb.isEmpty() ? "创作完成" : sb.toString();
    }

    /**
     * 从 toolCalls 中提取 publishArticle 参数里的文章内容
     */
    private String extractPublishContent(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull()) return null;
        for (JsonNode toolCall : toolCallsNode) {
            String toolName = toolCall.get("function").get("name").asText();
            if ("publishArticle".equals(toolName)) {
                String argsStr = toolCall.get("function").get("arguments").asText();
                try {
                    JsonNode args = objectMapper.readTree(argsStr);
                    if (args.has("content") && !args.get("content").isNull()) {
                        return args.get("content").asText();
                    }
                } catch (Exception e) {
                    log.warn("解析 publishArticle 参数失败", e);
                }
            }
        }
        return null;
    }

    /** 发送 JSON POST 请求 */
    private String postJson(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorMsg;
            try (var errReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = errReader.readLine()) != null) sb.append(line);
                errorMsg = sb.toString();
            } catch (Exception e) {
                errorMsg = "HTTP " + responseCode;
            }
            throw new RuntimeException("HTTP " + responseCode + " - " + errorMsg);
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
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
