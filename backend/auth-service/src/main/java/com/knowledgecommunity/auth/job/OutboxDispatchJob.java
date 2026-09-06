package com.knowledgecommunity.auth.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowledgecommunity.auth.user.entity.EventOutbox;
import com.knowledgecommunity.auth.user.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 事件投递定时任务（auth-service 域内职责，随 user 模块迁移）
 * 每30秒扫描 event_outbox 表中 status=0 的记录，投递到 RocketMQ
 * 投递成功 status=1，失败 retry+1，超过5次 status=3（死信）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatchJob {

    private final EventOutboxMapper eventOutboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /** 每30秒执行一次 */
    @Scheduled(fixedDelay = 30000)
    public void dispatch() {
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

        for (EventOutbox event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                rocketMQTemplate.syncSend(topic,
                        MessageBuilder.withPayload(event.getPayload())
                                .setHeader("eventType", event.getEventType())
                                .build());

                eventOutboxMapper.update(null,
                        new LambdaUpdateWrapper<EventOutbox>()
                                .eq(EventOutbox::getId, event.getId())
                                .set(EventOutbox::getStatus, 1)
                );
                log.info("Outbox事件投递成功: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
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
