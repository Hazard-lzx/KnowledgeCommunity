package com.knowledgecommunity.infrastructure.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.infrastructure.mq.producer.EventProducer;
import com.knowledgecommunity.modules.user.dto.FollowEventPayload;
import com.knowledgecommunity.modules.user.entity.EventOutbox;
import com.knowledgecommunity.modules.user.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 事件投递定时任务
 * 每30秒扫描 event_outbox 表中 status=0 的记录，投递到 RocketMQ
 * 投递成功 status=1，失败 retry+1，超过5次 status=3（死信）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatchJob {

    private final EventOutboxMapper eventOutboxMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** 每30秒执行一次 */
    @Scheduled(fixedDelay = 30000)
    public void dispatch() {
        // 扫描 status=0（待投递）的前200条记录
        List<EventOutbox> events = eventOutboxMapper.selectList(
                new LambdaQueryWrapper<EventOutbox>()
                        .eq(EventOutbox::getStatus, 0)
                        .orderByAsc(EventOutbox::getCreateTime)
                        .last("LIMIT 200")
        );

        if (events.isEmpty()) {
            return;
        }

        // 批量更新 status=2（发送中），防止重复投递
        List<Long> ids = events.stream().map(EventOutbox::getId).toList();
        eventOutboxMapper.update(null,
                new LambdaUpdateWrapper<EventOutbox>()
                        .in(EventOutbox::getId, ids)
                        .set(EventOutbox::getStatus, 2)
        );

        // 逐条发送到 MQ
        for (EventOutbox event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                rocketMQTemplate.syncSend(topic,
                        MessageBuilder.withPayload(event.getPayload())
                                .setHeader("eventType", event.getEventType())
                                .build());

                // 投递成功：status=1
                eventOutboxMapper.update(null,
                        new LambdaUpdateWrapper<EventOutbox>()
                                .eq(EventOutbox::getId, event.getId())
                                .set(EventOutbox::getStatus, 1)
                );
                log.info("Outbox事件投递成功: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                // 投递失败：retry+1，超过5次置 status=3（死信）
                int newRetry = event.getRetryCount() + 1;
                int newStatus = newRetry >= 5 ? 3 : 0;
                eventOutboxMapper.update(null,
                        new LambdaUpdateWrapper<EventOutbox>()
                                .eq(EventOutbox::getId, event.getId())
                                .set(EventOutbox::getRetryCount, newRetry)
                                .set(EventOutbox::getStatus, newStatus)
                );
                log.warn("Outbox事件投递失败: id={}, type={}, retry={}", event.getId(), event.getEventType(), newRetry);
            }
        }
    }

    /** 根据事件类型解析 MQ Topic */
    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "FOLLOWED", "UNFOLLOWED" -> "USER_RELATION_EVENT";
            default -> "DEFAULT_EVENT";
        };
    }
}
