package com.knowledgecommunity.feed.client;

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

    /**
     * 游标分页获取已发布文章
     * @param cursor 上一页最后一条的创建时间（yyyy-MM-dd HH:mm:ss）
     * @param size   条数
     * @param userIds 关注模式作者过滤（null=全站）
     */
    @GetMapping("/api/internal/articles/page")
    Result<List<ArticleBrief>> pageArticles(@RequestParam(value = "cursor", required = false) String cursor,
                                            @RequestParam("size") int size,
                                            @RequestParam(value = "userIds", required = false) List<Long> userIds);
}
