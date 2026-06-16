package com.knowledgecommunity.infrastructure.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章发布事件消费者
 * 监听 ARTICLE_PUBLISHED Topic，调用 DeepSeek Chat API 生成文章摘要
 * 摘要回写到 article.summary 字段
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "ARTICLE_PUBLISHED",
        consumerGroup = "article-published-consumer"
)
@RequiredArgsConstructor
public class ArticlePublishedConsumer implements RocketMQListener<String> {

    private final ArticleMapper articleMapper;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.chat-model}")
    private String chatModel;

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

            // 调用 DeepSeek Chat API 生成摘要
            String summary = generateSummary(content);

            // 回写 article.summary
            article.setSummary(summary);
            articleMapper.updateById(article);

            log.info("文章摘要生成完成, articleId={}", articleId);
        } catch (Exception e) {
            log.error("处理文章发布事件失败: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    private String generateSummary(String content) throws Exception {
        String url = baseUrl + "/v1/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", chatModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是一个专业的内容摘要生成器，请用简洁的中文总结以下文章的核心内容，不超过200字。"),
                Map.of("role", "user", "content", content)
        ));

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.getOutputStream().write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonNode node = objectMapper.readTree(sb.toString());
            return node.get("choices").get(0).get("message").get("content").asText();
        }
    }
}
