package com.knowledgecommunity.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI 问答服务：基于 RAG（检索增强生成）的文章问答
 *
 * 流程：
 * 1. 文章分块（按 ## 分割，每块不超过500字）
 * 2. 对每个块进行 Embedding，存入 Redis（文本 + 向量）
 * 3. 用户提问时，对问题进行 Embedding
 * 4. 在 Redis 中检索 Top3 相似块（余弦相似度）
 * 5. 拼接 Prompt，调用 DeepSeek Chat API（流式输出）
 * 6. 通过 SseEmitter 逐段推送前端
 *
 * 缓存策略：
 * - 文章索引状态：article:rag:indexed:{articleId}，24小时
 * - 块文本：article:rag:chunk:{articleId}:{i}:text，24小时
 * - 块向量：article:rag:chunk:{articleId}:{i}:vec，24小时
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.chat-model}")
    private String chatModel;

    @Value("${deepseek.embedding-base-url}")
    private String embeddingBaseUrl;

    @Value("${deepseek.embedding-api-key}")
    private String embeddingApiKey;

    @Value("${deepseek.embedding-model}")
    private String embeddingModel;

    /**
     * AI 问答入口
     * @param articleId 文章ID
     * @param question  用户问题
     * @return SseEmitter 流式推送
     */
    public SseEmitter ask(Long articleId, String question) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        SseEmitter emitter = new SseEmitter(300000L); // 超时5分钟

        new Thread(() -> {
            try {
                // 1. 检查是否已索引，若未索引则分块并存储向量
                String indexedKey = "article:rag:indexed:" + articleId;
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(indexedKey))) {
                    indexArticle(article);
                    redisTemplate.opsForValue().set(indexedKey, "1", 24, TimeUnit.HOURS);
                }

                // 2. 对 question 进行 Embedding
                float[] questionEmbedding = getEmbedding(question);

                // 3. 在 Redis 中检索 Top3 相似块
                List<String> chunks = retrieveTopKChunks(articleId, questionEmbedding, 3);

                // 4. 拼接 Prompt
                String context = String.join("\n\n", chunks);
                String prompt = "根据以下参考内容回答问题：\n" + context + "\n\n问题：" + question + "\n回答：";

                // 5. 调用 DeepSeek Chat API（stream=true），通过 SSE 推送
                streamChat(prompt, emitter);

            } catch (Exception e) {
                log.error("AI问答异常, articleId={}", articleId, e);
                try {
                    emitter.send(SseEmitter.event().data("[ERROR] " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 文章分块并索引
     * 按 ## 分割，每块不超过500字，对每块进行 Embedding 存入 Redis
     */
    private void indexArticle(Article article) throws Exception {
        String content = article.getContent();
        // 按 ## 分块，每块不超过500字
        String[] sections = content.split("(?=##)");
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String section : sections) {
            if (currentChunk.length() + section.length() > 500 && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            currentChunk.append(section);
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        // 对每个块进行 Embedding 并存入 Redis
        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = getEmbedding(chunks.get(i));
            String vectorKey = "article:rag:chunk:" + article.getId() + ":" + i;
            // 存储块文本和向量
            redisTemplate.opsForValue().set(vectorKey + ":text", chunks.get(i), 24, TimeUnit.HOURS);
            // 向量以逗号分隔的字符串存储
            String vectorStr = floatArrayToString(embedding);
            redisTemplate.opsForValue().set(vectorKey + ":vec", vectorStr, 24, TimeUnit.HOURS);
        }

        // 记录块数量
        redisTemplate.opsForValue().set("article:rag:chunkcount:" + article.getId(),
                String.valueOf(chunks.size()), 24, TimeUnit.HOURS);
    }

    /** 检索 TopK 相似块（余弦相似度排序） */
    private List<String> retrieveTopKChunks(Long articleId, float[] questionEmbedding, int k) {
        String countStr = redisTemplate.opsForValue().get("article:rag:chunkcount:" + articleId);
        int chunkCount = countStr != null ? Integer.parseInt(countStr) : 0;

        // 计算每个块的相似度
        List<Map.Entry<Integer, Double>> scores = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            String vectorKey = "article:rag:chunk:" + articleId + ":" + i + ":vec";
            String vecStr = redisTemplate.opsForValue().get(vectorKey);
            if (vecStr != null) {
                float[] chunkVec = stringToFloatArray(vecStr);
                double similarity = cosineSimilarity(questionEmbedding, chunkVec);
                scores.add(Map.entry(i, similarity));
            }
        }

        // 按相似度排序取 TopK
        scores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scores.size()); i++) {
            String textKey = "article:rag:chunk:" + articleId + ":" + scores.get(i).getKey() + ":text";
            String text = redisTemplate.opsForValue().get(textKey);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    /** 调用 Embedding API 获取文本向量 */
    private float[] getEmbedding(String text) throws Exception {
        String url = embeddingBaseUrl + "/embeddings";
        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel);
        body.put("input", text);

        String response = postJson(url, objectMapper.writeValueAsString(body), embeddingApiKey);
        JsonNode node = objectMapper.readTree(response);
        JsonNode embeddingNode = node.get("data").get(0).get("embedding");

        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }
        return embedding;
    }

    /** 调用 Chat API 流式输出，逐段通过 SseEmitter 推送 */
    private void streamChat(String prompt, SseEmitter emitter) throws Exception {
        String url = baseUrl + "/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", chatModel);
        body.put("stream", true);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setReadTimeout(60000);
        conn.setConnectTimeout(10000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    if (data.isEmpty()) continue;
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.get("choices").get(0).get("delta");
                    if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                        String content = delta.get("content").asText();
                        if (content != null && !content.isEmpty()) {
                            emitter.send(SseEmitter.event().data(content));
                        }
                    }
                }
            }
        } finally {
            conn.disconnect();
        }

        // 主动发送 [DONE] 信号，让前端知道流结束
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (Exception ignored) {}
        emitter.complete();
    }

    /** 发送 JSON POST 请求 */
    private String postJson(String urlStr, String jsonBody, String authKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + authKey);
        conn.setDoOutput(true);
        conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /** 计算余弦相似度 */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8);
    }

    private String floatArrayToString(float[] arr) {
        StringJoiner sj = new StringJoiner(",");
        for (float v : arr) {
            sj.add(String.valueOf(v));
        }
        return sj.toString();
    }

    private float[] stringToFloatArray(String str) {
        String[] parts = str.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Float.parseFloat(parts[i]);
        }
        return arr;
    }
}
