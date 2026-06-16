package com.knowledgecommunity.modules.interaction.controller;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.modules.interaction.dto.LikeResult;
import com.knowledgecommunity.modules.interaction.service.InteractionService;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 互动控制器：点赞/取消点赞、收藏/取消收藏
 */
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    /** 点赞文章 */
    @PostMapping("/{id}/like")
    public Result<LikeResult> like(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(interactionService.like(id, currentUser.getUserId()));
    }

    /** 取消点赞 */
    @DeleteMapping("/{id}/like")
    public Result<LikeResult> unlike(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(interactionService.unlike(id, currentUser.getUserId()));
    }

    /** 收藏文章 */
    @PostMapping("/{id}/collect")
    public Result<LikeResult> collect(@PathVariable Long id,
                                      @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(interactionService.collect(id, currentUser.getUserId()));
    }

    /** 取消收藏 */
    @DeleteMapping("/{id}/collect")
    public Result<LikeResult> uncollect(@PathVariable Long id,
                                        @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(interactionService.uncollect(id, currentUser.getUserId()));
    }
}
