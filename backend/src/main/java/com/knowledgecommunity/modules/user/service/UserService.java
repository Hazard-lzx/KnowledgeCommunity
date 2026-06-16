package com.knowledgecommunity.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import com.knowledgecommunity.modules.auth.entity.User;
import com.knowledgecommunity.modules.auth.mapper.UserMapper;
import com.knowledgecommunity.modules.user.dto.UpdateProfileRequest;
import com.knowledgecommunity.modules.user.dto.UserProfileResponse;
import com.knowledgecommunity.modules.user.entity.UserFollow;
import com.knowledgecommunity.modules.user.mapper.UserFollowMapper;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户服务：获取/更新用户资料
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final ArticleMapper articleMapper;

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

        // 文章数
        response.setArticleCount(Math.toIntExact(articleMapper.selectCount(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getUserId, userId)
                        .eq(Article::getStatus, 1)
        )));

        // 获赞总数（所有文章的 like_count 之和）
        // 简化实现：查询所有已发布文章的 likeCount 求和
        response.setLikeCount(articleMapper.selectList(
                        new LambdaQueryWrapper<Article>()
                                .eq(Article::getUserId, userId)
                                .eq(Article::getStatus, 1)
                                .select(Article::getLikeCount)
                ).stream()
                .mapToInt(a -> a.getLikeCount() != null ? a.getLikeCount() : 0)
                .sum());

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
