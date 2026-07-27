package com.knowledgecommunity.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.modules.auth.entity.User;
import com.knowledgecommunity.modules.auth.mapper.UserMapper;
import com.knowledgecommunity.modules.user.dto.FollowEventPayload;
import com.knowledgecommunity.modules.user.dto.FollowUserItem;
import com.knowledgecommunity.modules.user.entity.EventOutbox;
import com.knowledgecommunity.modules.user.entity.UserFollow;
import com.knowledgecommunity.modules.user.mapper.EventOutboxMapper;
import com.knowledgecommunity.modules.user.mapper.UserFollowMapper;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户关注服务
 * - 关注/取关操作在业务表中更新状态
 * - 同时写入 Outbox 表，由定时任务投递到 RocketMQ
 * - 保证最终一致性：业务操作与事件记录在同一事务中
 */
@Service
@RequiredArgsConstructor
public class UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final EventOutboxMapper eventOutboxMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    /**
     * 关注用户
     * - 不存在记录则新增，已取关则恢复状态
     * - 写入 Outbox 事件 FOLLOWED
     */
    @Transactional
    public void follow(UserPrincipal currentUser, Long followeeId) {
        if (currentUser.getUserId().equals(followeeId)) {
            throw new BusinessException(400, "不能关注自己");
        }

        UserFollow existing = userFollowMapper.selectOne(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, currentUser.getUserId())
                        .eq(UserFollow::getFolloweeId, followeeId)
        );

        if (existing == null) {
            // 首次关注，插入新记录
            UserFollow follow = new UserFollow();
            follow.setFollowerId(currentUser.getUserId());
            follow.setFolloweeId(followeeId);
            follow.setStatus(1);
            userFollowMapper.insert(follow);
        } else if (existing.getStatus() == 0) {
            // 之前取关过，恢复关注状态
            userFollowMapper.update(null,
                    new LambdaUpdateWrapper<UserFollow>()
                            .eq(UserFollow::getId, existing.getId())
                            .set(UserFollow::getStatus, 1)
            );
        }

        // 写入 Outbox 事件，等待定时任务投递 MQ
        insertOutboxEvent("FOLLOWED", currentUser.getUserId(), followeeId);

        // 清除关注 Feed 缓存，让下次访问立即看到最新数据
        clearFollowFeedCache(currentUser.getUserId());
    }

    /**
     * 取关用户
     * - 软删除：将 status 置为 0
     * - 写入 Outbox 事件 UNFOLLOWED
     */
    @Transactional
    public void unfollow(UserPrincipal currentUser, Long followeeId) {
        userFollowMapper.update(null,
                new LambdaUpdateWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, currentUser.getUserId())
                        .eq(UserFollow::getFolloweeId, followeeId)
                        .eq(UserFollow::getStatus, 1)
                        .set(UserFollow::getStatus, 0)
        );

        insertOutboxEvent("UNFOLLOWED", currentUser.getUserId(), followeeId);

        // 清除关注 Feed 缓存，让下次访问立即看到最新数据
        clearFollowFeedCache(currentUser.getUserId());
    }

    /**
     * 写入 Outbox 事件记录
     * @param eventType 事件类型（FOLLOWED / UNFOLLOWED）
     * @param followerId 关注者ID
     * @param followeeId 被关注者ID
     */
    private void insertOutboxEvent(String eventType, Long followerId, Long followeeId) {
        try {
            FollowEventPayload payload = new FollowEventPayload(followerId, followeeId);
            EventOutbox outbox = new EventOutbox();
            outbox.setAggregateType("USER_RELATION");
            outbox.setAggregateId(followerId + ":" + followeeId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setStatus(0); // 待投递
            outbox.setRetryCount(0);
            eventOutboxMapper.insert(outbox);
        } catch (JsonProcessingException e) {
            throw new BusinessException("序列化事件失败");
        }
    }

    /**
     * 清除关注 Feed 缓存（Redis L2），关注/取关后立即生效
     * 缓存 key 格式：feed:{userId}:following:*
     */
    private void clearFollowFeedCache(Long followerId) {
        String pattern = "feed:" + followerId + ":following:*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 获取关注列表（userId 关注了哪些人）
     * @param userId 目标用户ID
     * @param currentUser 当前登录用户（可为null）
     */
    public List<FollowUserItem> getFollowingList(Long userId, UserPrincipal currentUser) {
        // 查询关注关系
        List<UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .eq(UserFollow::getStatus, 1)
        );

        if (follows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> followeeIds = follows.stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toList());

        return buildFollowUserItems(followeeIds, currentUser);
    }

    /**
     * 获取粉丝列表（谁关注了 userId）
     * @param userId 目标用户ID
     * @param currentUser 当前登录用户（可为null）
     */
    public List<FollowUserItem> getFollowerList(Long userId, UserPrincipal currentUser) {
        // 查询粉丝关系
        List<UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, userId)
                        .eq(UserFollow::getStatus, 1)
        );

        if (follows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> followerIds = follows.stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toList());

        return buildFollowUserItems(followerIds, currentUser);
    }

    /**
     * 批量构建 FollowUserItem 列表
     * 包含用户信息和当前用户的关注状态
     */
    private List<FollowUserItem> buildFollowUserItems(List<Long> userIds, UserPrincipal currentUser) {
        // 批量查询用户信息
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 如果当前用户已登录，查询当前用户关注了列表中的哪些人
        Set<Long> myFolloweeIds = Collections.emptySet();
        if (currentUser != null) {
            myFolloweeIds = userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUser.getUserId())
                            .eq(UserFollow::getStatus, 1)
                            .in(UserFollow::getFolloweeId, userIds)
                            .select(UserFollow::getFolloweeId)
            ).stream()
                    .map(UserFollow::getFolloweeId)
                    .collect(Collectors.toSet());
        }

        Set<Long> finalMyFolloweeIds = myFolloweeIds;
        return userIds.stream()
                .map(id -> {
                    FollowUserItem item = new FollowUserItem();
                    item.setId(id);
                    User user = userMap.get(id);
                    item.setUsername(user != null ? user.getUsername() : "");
                    item.setAvatarUrl(user != null ? user.getAvatarUrl() : null);
                    item.setFollowed(finalMyFolloweeIds.contains(id));
                    return item;
                })
                .collect(Collectors.toList());
    }
}
