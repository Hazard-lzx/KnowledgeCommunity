package com.knowledgecommunity.modules.feed.controller;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.modules.feed.dto.FeedResponse;
import com.knowledgecommunity.modules.feed.service.FeedService;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Feed 控制器：获取首页瀑布流数据
 */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /**
     * 获取 Feed 流（游标分页，匿名用户也可访问）
     * @param mode 模式：all=全站最新，following=仅关注用户（需登录）
     */
    @GetMapping
    public Result<FeedResponse> getFeed(@Nullable @AuthenticationPrincipal UserPrincipal currentUser,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(defaultValue = "all") String mode) {
        return Result.success(feedService.getFeed(currentUser, cursor, size, mode));
    }
}
