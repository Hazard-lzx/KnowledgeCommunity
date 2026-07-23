package com.knowledgecommunity.modules.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 写作助手服务
 * 支持续写（continue）、润色（polish）、大纲（outline）三种模式
 * 通过 ChatClient 流式输出，以 SSE 事件格式推送前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingAssistantService {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * 写作助手入口
     * @param type    写作类型：continue / polish / outline
     * @param content 用户输入内容
     * @param context 文章其他部分作为背景参考
     * @param writer  SSE 输出流
     */
    public void assist(String type, String content, String context, PrintWriter writer) {
        try {
            String systemPrompt = buildSystemPrompt(type, context);
            String userPrompt = buildUserPrompt(type, content);

            AtomicBoolean hasContent = new AtomicBoolean(false);

            chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        hasContent.set(true);
                        sendSse(writer, "chunk", token);
                    })
                    .doOnComplete(() -> {
                        if (!hasContent.get()) {
                            sendSse(writer, "chunk", "暂无内容返回");
                        }
                        sendSse(writer, "done", "[DONE]");
                    })
                    .onErrorResume(e -> {
                        log.error("AI写作助手流式异常, type={}", type, e);
                        sendSse(writer, "error", e.getMessage());
                        return Mono.empty();
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("AI写作助手异常, type={}", type, e);
            sendSse(writer, "error", e.getMessage());
        }
        writer.flush();
    }

    private String buildSystemPrompt(String type, String context) {
        String base;
        switch (type) {
            case "continue":
                base = "你是一个专业的内容创作者。请根据用户提供的上文，自然流畅地续写100-200字，保持风格一致。直接输出续写内容，不要加任何前缀或解释。";
                break;
            case "polish":
                base = "你是一个专业的文字编辑。请优化以下文本的表达，修正语病，使其更流畅、专业，但保持原意不变。直接输出优化后的文本，不要加任何前缀或解释。";
                break;
            case "outline":
                base = "你是一个结构化的写作助手。请根据用户提供的标题，生成一份详细的文章大纲，包含至少3个二级标题，每个二级标题下包含2-3个要点。使用Markdown列表格式输出。";
                break;
            default:
                base = "你是一个写作助手。";
        }

        if (StringUtils.isNotBlank(context)) {
            base += "\n\n以下是文章的其他部分作为背景参考：\n" + context;
        }

        return base;
    }

    private String buildUserPrompt(String type, String content) {
        switch (type) {
            case "continue":
                return "上文：\n" + content + "\n\n请续写：";
            case "polish":
                return "原文：\n" + content + "\n\n优化后：";
            case "outline":
                return "标题：" + content + "\n\n大纲：";
            default:
                return content;
        }
    }

    /**
     * 发送 SSE 事件
     * 格式：event: <type>\ndata: <content>\n\n
     */
    private void sendSse(PrintWriter writer, String event, String data) {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
    }
}