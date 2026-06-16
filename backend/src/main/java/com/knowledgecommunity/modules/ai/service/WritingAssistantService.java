package com.knowledgecommunity.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingAssistantService {

    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.chat-model}")
    private String chatModel;

    public void handleWritingAssist(String type, String content, String context, PrintWriter writer) {
        try {
            String systemPrompt = buildSystemPrompt(type, context);
            String userPrompt = buildUserPrompt(type, content);
            doStreamChat(systemPrompt, userPrompt, writer);
        } catch (Exception e) {
            log.error("AI\u5199\u4f5c\u52a9\u624b\u5f02\u5e38, type={}", type, e);
            sendSse(writer, "[ERROR] " + e.getMessage());
        }
        writer.flush();
    }

    private String buildSystemPrompt(String type, String context) {
        String base;
        switch (type) {
            case "continue":
                base = "\u4f60\u662f\u4e00\u4e2a\u4e13\u4e1a\u7684\u5185\u5bb9\u521b\u4f5c\u8005\u3002\u8bf7\u6839\u636e\u7528\u6237\u63d0\u4f9b\u7684\u4e0a\u6587\uff0c\u81ea\u7136\u6d41\u7545\u5730\u7eed\u5199100-200\u5b57\uff0c\u4fdd\u6301\u98ce\u683c\u4e00\u81f4\u3002\u76f4\u63a5\u8f93\u51fa\u7eed\u5199\u5185\u5bb9\uff0c\u4e0d\u8981\u52a0\u4efb\u4f55\u524d\u7f00\u6216\u89e3\u91ca\u3002";
                break;
            case "polish":
                base = "\u4f60\u662f\u4e00\u4e2a\u4e13\u4e1a\u7684\u6587\u5b57\u7f16\u8f91\u3002\u8bf7\u4f18\u5316\u4ee5\u4e0b\u6587\u672c\u7684\u8868\u8fbe\uff0c\u4fee\u6b63\u8bed\u75c5\uff0c\u4f7f\u5176\u66f4\u6d41\u7545\u3001\u4e13\u4e1a\uff0c\u4f46\u4fdd\u6301\u539f\u610f\u4e0d\u53d8\u3002\u76f4\u63a5\u8f93\u51fa\u4f18\u5316\u540e\u7684\u6587\u672c\uff0c\u4e0d\u8981\u52a0\u4efb\u4f55\u524d\u7f00\u6216\u89e3\u91ca\u3002";
                break;
            case "outline":
                base = "\u4f60\u662f\u4e00\u4e2a\u7ed3\u6784\u5316\u7684\u5199\u4f5c\u52a9\u624b\u3002\u8bf7\u6839\u636e\u7528\u6237\u63d0\u4f9b\u7684\u6807\u9898\uff0c\u751f\u6210\u4e00\u4efd\u8be6\u7ec6\u7684\u6587\u7ae0\u5927\u7eb2\uff0c\u5305\u542b\u81f3\u5c113\u4e2a\u4e8c\u7ea7\u6807\u9898\uff0c\u6bcf\u4e2a\u4e8c\u7ea7\u6807\u9898\u4e0b\u5305\u542b2-3\u4e2a\u8981\u70b9\u3002\u4f7f\u7528Markdown\u5217\u8868\u683c\u5f0f\u8f93\u51fa\u3002";
                break;
            default:
                base = "\u4f60\u662f\u4e00\u4e2a\u5199\u4f5c\u52a9\u624b\u3002";
        }

        if (StringUtils.isNotBlank(context)) {
            base += "\n\n\u4ee5\u4e0b\u662f\u6587\u7ae0\u7684\u5176\u4ed6\u90e8\u5206\u4f5c\u4e3a\u80cc\u666f\u53c2\u8003\uff1a\n" + context;
        }

        return base;
    }

    private String buildUserPrompt(String type, String content) {
        switch (type) {
            case "continue":
                return "\u4e0a\u6587\uff1a\n" + content + "\n\n\u8bf7\u7eed\u5199\uff1a";
            case "polish":
                return "\u539f\u6587\uff1a\n" + content + "\n\n\u4f18\u5316\u540e\uff1a";
            case "outline":
                return "\u6807\u9898\uff1a" + content + "\n\n\u5927\u7eb2\uff1a";
            default:
                return content;
        }
    }

    private void doStreamChat(String systemPrompt, String userPrompt, PrintWriter writer) throws Exception {
        String url = baseUrl + "/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", chatModel);
        body.put("stream", true);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String requestBody = objectMapper.writeValueAsString(body);
        log.info("\u8c03\u7528AI\u5199\u4f5c\u52a9\u624bAPI: url={}", url);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorBody = "";
            try {
                BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = errReader.readLine()) != null) sb.append(l);
                errorBody = sb.toString();
            } catch (Exception ignored) {}
            throw new RuntimeException("API HTTP " + responseCode + ": " + errorBody);
        }

        boolean hasContent = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode choices = node.get("choices");
                    if (choices == null || choices.size() == 0) continue;
                    JsonNode delta = choices.get(0).get("delta");
                    if (delta == null) continue;
                    JsonNode contentNode = delta.get("content");
                    if (contentNode == null || contentNode.isNull()) continue;
                    String token = contentNode.asText();
                    if (token.isEmpty()) continue;
                    hasContent = true;
                    sendSse(writer, token);
                } catch (Exception e) {
                    log.warn("\u89e3\u6790SSE\u6570\u636e\u5931\u8d25: {}", data);
                }
            }
        }

        if (!hasContent) {
            sendSse(writer, "\u6682\u65e0\u5185\u5bb9\u8fd4\u56de");
        }

        sendSse(writer, "[DONE]");
        writer.flush();
    }

    private void sendSse(PrintWriter writer, String data) {
        writer.write("data: " + data + "\n\n");
        writer.flush();
    }
}