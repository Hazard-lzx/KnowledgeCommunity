package com.knowledgecommunity.article.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.article.article.entity.Article;
import com.knowledgecommunity.article.article.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 文章发布事件消费者
 * 监听 ARTICLE_PUBLISHED Topic，调用 ai-service 的 /internal/summary 接口生成摘要，
 * 摘要回写到 article.summary 字段（AI 能力已出城至 Python，Java 侧不再引 Spring AI）
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "ARTICLE_PUBLISHED",
        consumerGroup = "article-published-consumer"
)
public class ArticlePublishedConsumer implements RocketMQListener<String> {

    private final ArticleMapper articleMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String pythonBaseUrl;
    private final String internalToken;

    public ArticlePublishedConsumer(
            ArticleMapper articleMapper,
            ObjectMapper objectMapper,
            @Value("${internal.python-base-url}") String pythonBaseUrl,
            @Value("${internal.token}") String internalToken) {
        this.articleMapper = articleMapper;
        this.objectMapper = objectMapper;
        this.pythonBaseUrl = pythonBaseUrl;
        this.internalToken = internalToken;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void onMessage(String message) {
        try {
            Long articleId = Long.parseLong(message);
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                log.warn("文章不存在, articleId={}", articleId);
                return;
            }

            // 取文章内容前2000字
            String content = article.getContent();
            if (content.length() > 2000) {
                content = content.substring(0, 2000);
            }

            String summary = generateSummary(content);

            article.setSummary(summary);
            articleMapper.updateById(article);

            log.info("文章摘要生成完成, articleId={}", articleId);
        } catch (Exception e) {
            log.error("处理文章发布事件失败: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 调用 ai-service 内部摘要接口（失败抛异常触发 RocketMQ 重投）
     */
    private String generateSummary(String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("content", content));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonBaseUrl + "/internal/summary"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .header("X-Trace-Id", java.util.UUID.randomUUID().toString().replace("-", ""))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ai-service 摘要接口返回 " + response.statusCode() + ": " + response.body());
        }
        var result = objectMapper.readTree(response.body());
        if (result.path("code").asInt() != 200) {
            throw new IllegalStateException("ai-service 摘要接口业务失败: " + response.body());
        }
        return result.path("data").asText();
    }
}
