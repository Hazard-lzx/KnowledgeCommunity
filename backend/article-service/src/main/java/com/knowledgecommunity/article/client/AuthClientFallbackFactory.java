package com.knowledgecommunity.article.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserBrief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** auth-service 降级工厂：返回业务错误码，文章作者信息缺失不阻塞主流程 */
@Slf4j
@Component
public class AuthClientFallbackFactory implements FallbackFactory<AuthClient> {

    @Override
    public AuthClient create(Throwable cause) {
        return new AuthClient() {
            @Override
            public Result<List<UserBrief>> batchUsers(List<Long> ids) {
                log.warn("auth-service 熔断/不可用，文章作者信息降级: {}", cause.getMessage());
                return Result.error(503, "auth-service 暂不可用");
            }

            @Override
            public Result<Boolean> isFollowing(Long followerId, Long followeeId) {
                log.warn("auth-service 熔断/不可用，关注状态降级: {}", cause.getMessage());
                return Result.error(503, "auth-service 暂不可用");
            }
        };
    }
}
