package com.knowledgecommunity.auth.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserArticleStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** article-service 降级工厂：返回业务错误码，UserService 侧降级为 0 统计 */
@Slf4j
@Component
public class ArticleClientFallbackFactory implements FallbackFactory<ArticleClient> {

    @Override
    public ArticleClient create(Throwable cause) {
        return userId -> {
            log.warn("article-service 熔断/不可用，用户文章统计降级: {}", cause.getMessage());
            return Result.error(503, "article-service 暂不可用");
        };
    }
}
