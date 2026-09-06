package com.knowledgecommunity.feed.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.ArticleBrief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** article-service 降级工厂：返回业务错误码，FeedService 侧降级为空列表 */
@Slf4j
@Component
public class ArticleClientFallbackFactory implements FallbackFactory<ArticleClient> {

    @Override
    public ArticleClient create(Throwable cause) {
        return (cursor, size, userIds) -> {
            log.warn("article-service 熔断/不可用，Feed 文章分页降级: {}", cause.getMessage());
            return Result.error(503, "article-service 暂不可用");
        };
    }
}
