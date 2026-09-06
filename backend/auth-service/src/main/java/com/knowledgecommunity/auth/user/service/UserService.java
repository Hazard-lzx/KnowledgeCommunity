package com.knowledgecommunity.auth.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserArticleStats;
import com.knowledgecommunity.security.UserPrincipal;
import com.knowledgecommunity.auth.auth.entity.User;
import com.knowledgecommunity.auth.auth.mapper.UserMapper;
import com.knowledgecommunity.auth.client.ArticleClient;
import com.knowledgecommunity.auth.user.dto.UpdateProfileRequest;
import com.knowledgecommunity.auth.user.dto.UserProfileResponse;
import com.knowledgecommunity.auth.user.entity.UserFollow;
import com.knowledgecommunity.auth.user.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户服务：获取/更新用户资料
 * 文章数/获赞总数经 Feign 调 article-service 内部接口获取（article 表所有权已移交）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final ArticleClient articleClient;

    /**
     * 获取用户资料
     * @param userId       目标用户ID
     * @param currentUser  当前登录用户（可为 null）
     */
    public UserProfileResponse getProfile(Long userId, UserPrincipal currentUser) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setSignature(user.getSignature());

        // 关注数
        response.setFollowingCount(Math.toIntExact(userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .eq(UserFollow::getStatus, 1)
        )));

        // 粉丝数
        response.setFollowerCount(Math.toIntExact(userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, userId)
                        .eq(UserFollow::getStatus, 1)
        )));

        // 文章数 / 获赞总数（article-service 内部接口；失败降级为 0，不阻塞资料页）
        UserArticleStats stats = fetchArticleStats(userId);
        response.setArticleCount(stats.getArticleCount());
        response.setLikeCount(stats.getLikeCount());

        // 当前用户是否已关注该用户
        if (currentUser != null && !currentUser.getUserId().equals(userId)) {
            Long count = userFollowMapper.selectCount(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUser.getUserId())
                            .eq(UserFollow::getFolloweeId, userId)
                            .eq(UserFollow::getStatus, 1)
            );
            response.setFollowed(count > 0);
        } else {
            response.setFollowed(false);
        }

        return response;
    }

    private UserArticleStats fetchArticleStats(Long userId) {
        try {
            Result<UserArticleStats> result = articleClient.getUserArticleStats(userId);
            if (result.getCode() == 200 && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("获取用户文章统计失败（降级为0）: userId={}", userId, e);
        }
        return new UserArticleStats(0, 0);
    }

    /**
     * 更新当前用户资料
     */
    public void updateProfile(UserPrincipal currentUser, UpdateProfileRequest request) {
        User user = userMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (request.getUsername() != null) {
            // 检查用户名是否已被占用
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, request.getUsername())
                            .ne(User::getId, currentUser.getUserId())
            );
            if (count > 0) {
                throw new BusinessException(400, "用户名已被占用");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getSignature() != null) {
            user.setSignature(request.getSignature());
        }

        userMapper.updateById(user);
    }
}
