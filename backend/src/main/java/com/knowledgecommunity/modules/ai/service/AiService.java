package com.knowledgecommunity.modules.ai.service;

import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI 问答服务：基于 RAG（检索增强生成）的文章问答
 *
 * 流程：
 * 1. 文章分块（按 ## 分割，每块不超过 CHUNK_MAX_LENGTH 字）
 * 2. 对每个块进行 Embedding，存入 Redis（文本 + 向量）
 * 3. 用户提问时，对问题进行 Embedding
 * 4. 在 Redis 中检索 TopK 相似块（余弦相似度）
 * 5. 拼接 Prompt，调用 ChatClient（流式输出）
 * 6. 通过 SseEmitter 逐段推送前端
 *
 * 缓存策略：
 * - 文章索引状态：article:rag:indexed:{articleId}，CACHE_TTL_HOURS 小时
 * - 块文本：article:rag:chunk:{articleId}:{i}:text，CACHE_TTL_HOURS 小时
 * - 块向量：article:rag:chunk:{articleId}:{i}:vec，CACHE_TTL_HOURS 小时
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final int CHUNK_MAX_LENGTH = 500;
    private static final int TOP_K = 3;
    private static final int CACHE_TTL_HOURS = 24;

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;

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

        try {
            // 1. 检查是否已索引，若未索引则分块并存储向量
            String indexedKey = "article:rag:indexed:" + articleId;
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(indexedKey))) {
                indexArticle(article);
                redisTemplate.opsForValue().set(indexedKey, "1", CACHE_TTL_HOURS, TimeUnit.HOURS);
            }

            // 2. 对 question 进行 Embedding
            float[] questionEmbedding = doEmbed(question);

            // 3. 在 Redis 中检索 TopK 相似块
            List<String> chunks = retrieveTopK(articleId, questionEmbedding, TOP_K);

            // 4. 拼接 Prompt
            String prompt = buildPrompt(question, chunks);

            // 5. 调用 ChatClient 流式输出，通过 SseEmitter 推送
            Disposable subscription = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .subscribe(
                            content -> {
                                try {
                                    emitter.send(SseEmitter.event().data(content));
                                } catch (Exception e) {
                                    log.error("SSE推送失败", e);
                                }
                            },
                            error -> {
                                log.error("AI问答流式异常, articleId={}", articleId, error);
                                emitter.completeWithError(error);
                            },
                            () -> {
                                try {
                                    emitter.send(SseEmitter.event().data("[DONE]"));
                                    emitter.complete();
                                } catch (Exception e) {
                                    log.error("SSE完成信号发送失败", e);
                                }
                            }
                    );

            emitter.onCompletion(subscription::dispose);
            emitter.onTimeout(subscription::dispose);

        } catch (Exception e) {
            log.error("AI问答异常, articleId={}", articleId, e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 拼接 Prompt：将检索到的参考块与用户问题组合
     */
    private String buildPrompt(String question, List<String> chunks) {
        String context = String.join("\n\n", chunks);
        return "根据以下参考内容回答问题：\n" + context + "\n\n问题：" + question + "\n回答：";
    }

    /**
     * 文本向量化（Embedding）
     * Spring AI 1.0.0 EmbeddingModel.embed(text) 返回 float[]
     */
    private float[] doEmbed(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 文章分块并索引
     * 按 ## 分割，每块不超过 CHUNK_MAX_LENGTH 字，对每块进行 Embedding 存入 Redis
     */
    private void indexArticle(Article article) {
        String content = article.getContent();
        List<String> chunks = splitIntoChunks(content);

        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = doEmbed(chunks.get(i));
            String vectorKey = "article:rag:chunk:" + article.getId() + ":" + i;
            // 存储块文本和向量
            redisTemplate.opsForValue().set(vectorKey + ":text", chunks.get(i), CACHE_TTL_HOURS, TimeUnit.HOURS);
            String vectorStr = serializeVector(embedding);
            redisTemplate.opsForValue().set(vectorKey + ":vec", vectorStr, CACHE_TTL_HOURS, TimeUnit.HOURS);
        }

        // 记录块数量
        redisTemplate.opsForValue().set("article:rag:chunkcount:" + article.getId(),
                String.valueOf(chunks.size()), CACHE_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 按 ## 分割文章内容，每块不超过 CHUNK_MAX_LENGTH 字
     */
    private List<String> splitIntoChunks(String content) {
        String[] sections = content.split("(?=##)");
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String section : sections) {
            if (currentChunk.length() + section.length() > CHUNK_MAX_LENGTH && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            currentChunk.append(section);
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    /**
     * 检索 TopK 相似块（余弦相似度排序）
     */
    private List<String> retrieveTopK(Long articleId, float[] questionEmbedding, int k) {
        String countStr = redisTemplate.opsForValue().get("article:rag:chunkcount:" + articleId);
        int chunkCount = countStr != null ? Integer.parseInt(countStr) : 0;

        // 计算每个块的相似度
        List<Map.Entry<Integer, Double>> scores = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            String vectorKey = "article:rag:chunk:" + articleId + ":" + i + ":vec";
            String vecStr = redisTemplate.opsForValue().get(vectorKey);
            if (vecStr != null) {
                float[] chunkVec = deserializeVector(vecStr);
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

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8);
    }

    /**
     * 向量序列化：float[] -> 逗号分隔字符串
     */
    private String serializeVector(float[] arr) {
        StringJoiner sj = new StringJoiner(",");
        for (float v : arr) {
            sj.add(String.valueOf(v));
        }
        return sj.toString();
    }

    /**
     * 向量反序列化：逗号分隔字符串 -> float[]
     */
    private float[] deserializeVector(String str) {
        String[] parts = str.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Float.parseFloat(parts[i]);
        }
        return arr;
    }
}