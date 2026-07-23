package com.knowledgecommunity.infrastructure.mq.consumer;

import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 文章发布事件消费者
 * 监听 ARTICLE_PUBLISHED Topic，调用 ChatClient 生成文章摘要
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
    private final ChatClient.Builder chatClientBuilder;

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

            // 调用 ChatClient 生成摘要
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

    /**
     * 调用 ChatClient 生成文章摘要
     */
    private String generateSummary(String content) {
        return chatClientBuilder.build()
                .prompt()
                .system("你是一个专业的内容摘要生成器，请用简洁的中文总结以下文章的核心内容，不超过200字。")
                .user(content)
                .call()
                .content();
    }
}