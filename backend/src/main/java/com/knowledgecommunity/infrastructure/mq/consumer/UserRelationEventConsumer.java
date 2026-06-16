package com.knowledgecommunity.infrastructure.mq.consumer;

import com.knowledgecommunity.modules.user.entity.UserFollow;
import com.knowledgecommunity.modules.user.mapper.UserFollowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户关系事件消费者
 * 监听 USER_RELATION_EVENT Topic，幂等地更新关注数和粉丝数到 Redis
 * 幂等策略：每次消费重新从 DB 统计 count，而非增量更新
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "USER_RELATION_EVENT",
        consumerGroup = "user-relation-consumer"
)
@RequiredArgsConstructor
public class UserRelationEventConsumer implements RocketMQListener<String> {

    private final StringRedisTemplate redisTemplate;
    private final UserFollowMapper userFollowMapper;

    @Override
    public void onMessage(String message) {
        try {
            String[] parts = message.split(":");
            Long followerId = Long.parseLong(parts[0]);
            Long followeeId = Long.parseLong(parts[1]);

            // 幂等：重新计算关注数和粉丝数
            long followingCount = userFollowMapper.selectCount(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, followerId)
                            .eq(UserFollow::getStatus, 1)
            );
            long followerCount = userFollowMapper.selectCount(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFolloweeId, followeeId)
                            .eq(UserFollow::getStatus, 1)
            );

            redisTemplate.opsForValue().set("user:following:" + followerId, String.valueOf(followingCount));
            redisTemplate.opsForValue().set("user:follower:" + followeeId, String.valueOf(followerCount));

            log.info("更新用户关系计数: followerId={}, followingCount={}, followeeId={}, followerCount={}",
                    followerId, followingCount, followeeId, followerCount);
        } catch (Exception e) {
            log.error("处理用户关系事件失败: {}", message, e);
            throw e;
        }
    }
}
