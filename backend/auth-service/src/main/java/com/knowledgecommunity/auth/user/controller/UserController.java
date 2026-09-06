package com.knowledgecommunity.auth.user.controller;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.security.UserPrincipal;
import com.knowledgecommunity.auth.user.dto.FollowUserItem;
import com.knowledgecommunity.auth.user.dto.UpdateProfileRequest;
import com.knowledgecommunity.auth.user.dto.UserProfileResponse;
import com.knowledgecommunity.auth.user.service.UserFollowService;
import com.knowledgecommunity.auth.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户控制器：获取/更新用户信息、关注/取关
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserFollowService userFollowService;
    private final UserService userService;

    /** 获取用户资料（匿名用户也可访问） */
    @GetMapping("/{id}")
    public Result<UserProfileResponse> getProfile(@PathVariable Long id,
                                                  @Nullable @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(userService.getProfile(id, currentUser));
    }

    /** 更新当前用户资料 */
    @PutMapping("/me")
    public Result<Void> updateProfile(@AuthenticationPrincipal UserPrincipal currentUser,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(currentUser, request);
        return Result.success();
    }

    /** 关注用户 */
    @PostMapping("/{followeeId}/follow")
    public Result<Void> follow(@AuthenticationPrincipal UserPrincipal currentUser,
                               @PathVariable Long followeeId) {
        userFollowService.follow(currentUser, followeeId);
        return Result.success();
    }

    /** 取关用户 */
    @DeleteMapping("/{followeeId}/follow")
    public Result<Void> unfollow(@AuthenticationPrincipal UserPrincipal currentUser,
                                 @PathVariable Long followeeId) {
        userFollowService.unfollow(currentUser, followeeId);
        return Result.success();
    }

    /** 获取关注列表*/
    @GetMapping("/{id}/following")
    public Result<List<FollowUserItem>> getFollowingList(@PathVariable Long id,
                                                         @Nullable @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(userFollowService.getFollowingList(id, currentUser));
    }

    /** 获取粉丝列表*/
    @GetMapping("/{id}/followers")
    public Result<List<FollowUserItem>> getFollowerList(@PathVariable Long id,
                                                        @Nullable @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(userFollowService.getFollowerList(id, currentUser));
    }
}
