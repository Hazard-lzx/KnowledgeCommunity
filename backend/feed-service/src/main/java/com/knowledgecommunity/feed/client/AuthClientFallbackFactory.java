package com.knowledgecommunity.feed.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserBrief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** auth-service 降级工厂：返回业务错误码，FeedService 侧降级为空 */
@Slf4j
@Component
public class AuthClientFallbackFactory implements FallbackFactory<AuthClient> {

    @Override
    public AuthClient create(Throwable cause) {
        return new AuthClient() {
            @Override
            public Result<List<Long>> followingIds(Long id) {
                log.warn("auth-service 熔断/不可用，关注列表降级: {}", cause.getMessage());
                return Result.error(503, "auth-service 暂不可用");
            }

            @Override
            public Result<List<UserBrief>> batchUsers(List<Long> ids) {
                log.warn("auth-service 熔断/不可用，作者信息降级: {}", cause.getMessage());
                return Result.error(503, "auth-service 暂不可用");
            }
        };
    }
}
