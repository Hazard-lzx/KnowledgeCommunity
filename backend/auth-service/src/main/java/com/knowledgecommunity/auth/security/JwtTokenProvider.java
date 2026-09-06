package com.knowledgecommunity.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 令牌提供者（仅签发与失效；入站验签已由网关 AuthGlobalFilter 完成，本服务不解析入站 JWT）
 * - 签名算法 HS256，密钥与网关一致（jwt.secret，存于 application-dev.yml）
 * - Redis 白名单：token:valid:{jti}=userId，过期时间与 JWT 相同
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private final StringRedisTemplate redisTemplate;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT 令牌并写入 Redis 白名单
     */
    public String createToken(Long userId, String username) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("jti", jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();

        redisTemplate.opsForValue().set("token:valid:" + jti, String.valueOf(userId), expiration, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add("user:tokens:" + userId, jti);
        redisTemplate.expire("user:tokens:" + userId, expiration, TimeUnit.MILLISECONDS);

        return token;
    }

    /**
     * 失效单个令牌（登出）：删除 Redis 白名单记录并从用户令牌集合移除
     */
    public void invalidateToken(String token) {
        var claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
        String jti = claims.get("jti", String.class);
        String userId = claims.getSubject();
        redisTemplate.delete("token:valid:" + jti);
        redisTemplate.opsForSet().remove("user:tokens:" + userId, jti);
    }

    /** 获取令牌过期时间（毫秒） */
    public long getExpiration() {
        return expiration;
    }
}
