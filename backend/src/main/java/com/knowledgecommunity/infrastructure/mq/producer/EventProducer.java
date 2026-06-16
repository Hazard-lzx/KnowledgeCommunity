package com.knowledgecommunity.infrastructure.mq.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 事件生产者：发送消息到 RocketMQ
 * 注意：关注/取关事件通过 Outbox 模式投递，此 Producer 主要用于直接发送场景
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /** 发送用户关系事件（关注/取关） */
    public void sendUserRelationEvent(String eventType, Long followerId, Long followeeId) {
        String payload = followerId + ":" + followeeId;
        rocketMQTemplate.syncSend("USER_RELATION_EVENT",
                MessageBuilder.withPayload(payload)
                        .setHeader("eventType", eventType)
                        .build());
        log.info("发送用户关系事件: type={}, followerId={}, followeeId={}", eventType, followerId, followeeId);
    }

    /** 发送文章发布事件（触发 AI 摘要生成） */
    public void sendArticlePublishedEvent(Long articleId) {
        rocketMQTemplate.syncSend("ARTICLE_PUBLISHED",
                MessageBuilder.withPayload(articleId).build());
        log.info("发送文章发布事件: articleId={}", articleId);
    }
}
