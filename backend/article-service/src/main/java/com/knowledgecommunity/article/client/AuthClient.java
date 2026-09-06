package com.knowledgecommunity.article.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * auth-service 内部接口客户端（自动携带 X-Internal-Token，见 common InternalFeignConfig）
 */
@FeignClient(name = "auth-service", fallbackFactory = AuthClientFallbackFactory.class)
public interface AuthClient {

    /** 批量获取用户简要信息（文章详情/列表的作者信息） */
    @GetMapping("/api/internal/users/batch")
    Result<List<UserBrief>> batchUsers(@RequestParam("ids") List<Long> ids);

    /** 查询 followerId 是否关注了 followeeId（文章详情页关注标记） */
    @GetMapping("/api/internal/users/follow/check")
    Result<Boolean> isFollowing(@RequestParam("followerId") Long followerId,
                                @RequestParam("followeeId") Long followeeId);
}
