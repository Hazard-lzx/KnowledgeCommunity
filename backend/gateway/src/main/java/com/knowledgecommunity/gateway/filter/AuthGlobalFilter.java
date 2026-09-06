package com.knowledgecommunity.gateway.filter;

import com.knowledgecommunity.gateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 统一鉴权 filter：只做认证，不做授权。
 * 1. 剥离外部请求自带的 X-User-* / X-Internal-Token 头（防伪造）
 * 2. 白名单放行（与单体 SecurityConfig 一致）
 * 3. JWT 验签 + Redis token 白名单检查（token:valid:{jti}，响应式非阻塞）
 * 4. 验证通过后注入 X-User-Id / X-Username 身份头
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // 与单体 SecurityConfig 的 permitAll 规则保持一致
    private static final List<String> ANY_METHOD_WHITELIST = List.of(
            "/api/auth/**",
            "/api/search/**"
    );
    private static final List<String> GET_ONLY_WHITELIST = List.of(
            "/api/feed/**",
            "/api/articles/**",
            "/api/users/**"
    );

    // 外部请求一律剥离，网关注入的身份头才可信
    private static final List<String> STRIPPED_HEADERS = List.of(
            "X-User-Id",
            "X-Username",
            "X-Internal-Token"
    );

    // 内部接口只允许内网直连，网关整体封锁（即使带有效 JWT 也不放行）
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private static final String TOKEN_VALID_KEY_PREFIX = "token:valid:";

    private final JwtValidator jwtValidator;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthGlobalFilter(JwtValidator jwtValidator, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtValidator = jwtValidator;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 内部接口不允许经网关访问，无论是否携带令牌
        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            return notFound(exchange);
        }

        // 无论是否白名单，外部自带的身份头必须先剥离
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        requestBuilder.headers(headers -> STRIPPED_HEADERS.forEach(headers::remove));

        // traceId：入口生成（外部传入的合法值沿用），注入请求头透传给全部下游服务
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        if (!StringUtils.hasText(traceId) || traceId.length() > 64) {
            traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        requestBuilder.header("X-Trace-Id", traceId);

        if (isWhitelisted(path, method)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        String token = resolveToken(request);
        if (token == null) {
            return unauthorized(exchange);
        }

        Claims claims = jwtValidator.parse(token);
        if (claims == null) {
            return unauthorized(exchange);
        }

        String userId = claims.getSubject();
        String jti = claims.get("jti", String.class);
        String username = claims.get("username", String.class);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(jti)) {
            return unauthorized(exchange);
        }

        // Redis token 白名单检查（已登出/被踢出的 token 在此被拒），必须响应式非阻塞
        return redisTemplate.opsForValue().get(TOKEN_VALID_KEY_PREFIX + jti)
                .map(storedUserId -> userId.equals(storedUserId))
                .defaultIfEmpty(false)
                .flatMap(valid -> {
                    if (!valid) {
                        return unauthorized(exchange);
                    }
                    ServerHttpRequest authenticatedRequest = requestBuilder
                            .headers(headers -> {
                                headers.set("X-User-Id", userId);
                                if (username != null) {
                                    headers.set("X-Username", username);
                                }
                            })
                            .build();
                    log.debug("Authenticated request: path={}, userId={}", path, userId);
                    return chain.filter(exchange.mutate().request(authenticatedRequest).build());
                });
    }

    private boolean isWhitelisted(String path, HttpMethod method) {
        for (String pattern : ANY_METHOD_WHITELIST) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        if (HttpMethod.GET.equals(method)) {
            for (String pattern : GET_ONLY_WHITELIST) {
                if (PATH_MATCHER.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7).trim();
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }

    /**
     * 401 响应体与单体 Result<T> 结构一致，前端 axios 拦截器按 HTTP 401 处理
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /** 内部路径对外一律表现为不存在 */
    private Mono<Void> notFound(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":404,\"message\":\"接口不存在\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
