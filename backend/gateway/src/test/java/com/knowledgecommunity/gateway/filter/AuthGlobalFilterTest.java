package com.knowledgecommunity.gateway.filter;

import com.knowledgecommunity.gateway.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 网关统一鉴权 filter 专项测试：
 * 伪造身份头剥离、白名单放行、JWT 验签、Redis token 白名单、身份头注入
 */
class AuthGlobalFilterTest {

    private static final String SECRET = "YourSuperSecretKeyForHS256AlgorithmMustBeAtLeast256BitsLong!!";
    private static final String VALID_JTI = "test-jti-123";

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private AuthGlobalFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new AuthGlobalFilter(new JwtValidator(SECRET), redisTemplate);
    }

    private String tokenFor(String userId, String username, String jti) {
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("jti", jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private GatewayFilterChain capturingChain(AtomicReference<ServerHttpRequest> captured) {
        return exchange -> {
            captured.set(exchange.getRequest());
            return Mono.empty();
        };
    }

    @Test
    void 伪造身份头在白名单路径必须被剥离且请求放行() {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/articles/1")
                        .header("X-User-Id", "999")
                        .header("X-Username", "hacker")
                        .header("X-Internal-Token", "fake-internal-token")
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        HttpHeaders headers = captured.get().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-Username")).isNull();
        assertThat(headers.getFirst("X-Internal-Token")).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 白名单接口无token可匿名访问() {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/feed").build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void auth路径任意方法白名单放行() {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
    }

    @Test
    void 非白名单无token返回401且不转发() {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles").build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":401");
    }

    @Test
    void GET文章路径白名单但POST同路径需认证() {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange postLike = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles/1/like").build());

        filter.filter(postLike, capturingChain(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(postLike.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 有效token验证通过后注入身份头() {
        when(valueOps.get("token:valid:" + VALID_JTI)).thenReturn(Mono.just("1"));
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("1", "alice", VALID_JTI))
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        HttpHeaders headers = captured.get().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("1");
        assertThat(headers.getFirst("X-Username")).isEqualTo("alice");
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 伪造身份头与有效token同时存在时以网关注入值覆盖() {
        when(valueOps.get("token:valid:" + VALID_JTI)).thenReturn(Mono.just("1"));
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("1", "alice", VALID_JTI))
                        .header("X-User-Id", "999")
                        .header("X-Username", "hacker")
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        HttpHeaders headers = captured.get().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("1");
        assertThat(headers.getFirst("X-Username")).isEqualTo("alice");
    }

    @Test
    void 已登出token在Redis白名单缺失时返回401() {
        when(valueOps.get("token:valid:" + VALID_JTI)).thenReturn(Mono.empty());
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("1", "alice", VALID_JTI))
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void Redis白名单值与token主体不一致时返回401() {
        when(valueOps.get("token:valid:" + VALID_JTI)).thenReturn(Mono.just("2"));
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("1", "alice", VALID_JTI))
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 内部接口路径即使携带有效token也被网关封锁() {
        when(valueOps.get("token:valid:" + VALID_JTI)).thenReturn(Mono.just("1"));
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/internal/articles/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("1", "alice", VALID_JTI))
                        .header("X-Internal-Token", "stolen-token")
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 签名非法的token返回401() {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        String forgedToken = Jwts.builder()
                .subject("1")
                .claim("username", "attacker")
                .claim("jti", VALID_JTI)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(("another-secret-key-" + SECRET).getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken)
                        .build());

        filter.filter(exchange, capturingChain(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
