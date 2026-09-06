package com.knowledgecommunity.feed.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * auth-service 内部接口客户端（自动携带 X-Internal-Token）
 */
@FeignClient(name = "auth-service", fallbackFactory = AuthClientFallbackFactory.class)
public interface AuthClient {

    /** 获取用户关注的ID列表（following 模式） */
    @GetMapping("/api/internal/users/{id}/following-ids")
    Result<List<Long>> followingIds(@PathVariable("id") Long id);

    /** 批量获取用户简要信息（Feed 作者信息） */
    @GetMapping("/api/internal/users/batch")
    Result<List<UserBrief>> batchUsers(@RequestParam("ids") List<Long> ids);
}
