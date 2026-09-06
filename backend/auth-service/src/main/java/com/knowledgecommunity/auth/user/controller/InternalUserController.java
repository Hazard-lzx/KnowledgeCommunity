package com.knowledgecommunity.auth.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserBrief;
import com.knowledgecommunity.auth.auth.entity.User;
import com.knowledgecommunity.auth.auth.mapper.UserMapper;
import com.knowledgecommunity.auth.user.entity.UserFollow;
import com.knowledgecommunity.auth.user.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 内部接口（仅供服务间 Feign 调用，经 InternalTokenFilter 校验 X-Internal-Token）
 * - GET /api/internal/users/batch?ids=1,2,3   批量获取用户简要信息（article/search/feed 补全作者）
 * - GET /api/internal/users/{id}/following-ids 获取关注ID列表（feed-service 关注模式）
 * - GET /api/internal/users/follow/check       是否已关注（article-service 文章详情页关注标记）
 */
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;

    /** 批量获取用户简要信息 */
    @GetMapping("/batch")
    public Result<List<UserBrief>> batchUsers(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<UserBrief> users = userMapper.selectBatchIds(ids).stream()
                .map(u -> {
                    UserBrief brief = new UserBrief();
                    brief.setId(u.getId());
                    brief.setUsername(u.getUsername());
                    brief.setAvatarUrl(u.getAvatarUrl());
                    return brief;
                })
                .collect(Collectors.toList());
        return Result.success(users);
    }

    /** 获取用户关注的ID列表 */
    @GetMapping("/{id}/following-ids")
    public Result<List<Long>> followingIds(@PathVariable Long id) {
        List<Long> ids = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, id)
                        .eq(UserFollow::getStatus, 1)
                        .select(UserFollow::getFolloweeId)
        ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toList());
        return Result.success(ids);
    }

    /** 查询 followerId 是否关注了 followeeId */
    @GetMapping("/follow/check")
    public Result<Boolean> isFollowing(@RequestParam Long followerId, @RequestParam Long followeeId) {
        boolean exists = userFollowMapper.exists(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFolloweeId, followeeId)
                        .eq(UserFollow::getStatus, 1)
        );
        return Result.success(exists);
    }
}
