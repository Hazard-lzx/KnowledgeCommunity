package com.knowledgecommunity.search.client;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.UserBrief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** auth-service 降级工厂：返回业务错误码，SearchService 侧降级为空 */
@Slf4j
@Component
public class AuthClientFallbackFactory implements FallbackFactory<AuthClient> {

    @Override
    public AuthClient create(Throwable cause) {
        return ids -> {
            log.warn("auth-service 熔断/不可用，搜索结果作者信息降级: {}", cause.getMessage());
            return Result.error(503, "auth-service 暂不可用");
        };
    }
}
