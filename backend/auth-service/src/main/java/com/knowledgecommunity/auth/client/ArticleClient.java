package com.knowledgecommunity.auth.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserArticleStats;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * article-service 内部接口客户端（自动携带 X-Internal-Token，见 common InternalFeignConfig）
 */
@FeignClient(name = "article-service", fallbackFactory = ArticleClientFallbackFactory.class)
public interface ArticleClient {

    /** 用户文章统计（用户资料页的文章数/获赞总数） */
    @GetMapping("/api/internal/articles/stats/{userId}")
    Result<UserArticleStats> getUserArticleStats(@PathVariable("userId") Long userId);
}
