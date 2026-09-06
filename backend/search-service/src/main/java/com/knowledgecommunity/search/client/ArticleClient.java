package com.knowledgecommunity.search.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.ArticleBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * article-service 内部接口客户端（自动携带 X-Internal-Token）
 */
@FeignClient(name = "article-service", fallbackFactory = ArticleClientFallbackFactory.class)
public interface ArticleClient {

    /** 批量获取文章简要信息（搜索结果补发布时间等 DB 字段） */
    @GetMapping("/api/internal/articles/batch")
    Result<List<ArticleBrief>> batchArticles(@RequestParam("ids") List<Long> ids);
}
