package com.knowledgecommunity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
 * JWT 令牌提供者
 * - 签名算法 HS256，密钥读取 jwt.secret
 * - 令牌 payload 含 userId、username、jti(UUID)、iat、exp(2小时)
 * - Redis 白名单机制：token:valid:{jti}=userId，过期时间与 JWT 相同
 * - 主动失效：维护 user:tokens:{userId} Set 存储该用户所有活跃 jti
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

    /** 获取 HMAC-SHA256 签名密钥 */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT 令牌
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT 令牌字符串
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

        // 写入 Redis 白名单：token:valid:{jti}=userId，过期时间与 JWT 一致
        redisTemplate.opsForValue().set("token:valid:" + jti, String.valueOf(userId), expiration, TimeUnit.MILLISECONDS);

        // 维护用户活跃令牌集合，用于批量失效（如修改密码、踢出用户）
        redisTemplate.opsForSet().add("user:tokens:" + userId, jti);
        redisTemplate.expire("user:tokens:" + userId, expiration, TimeUnit.MILLISECONDS);

        return token;
    }

    /**
     * 校验令牌签名和过期时间
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 Redis 白名单校验令牌是否有效（未被主动失效）
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            String jti = claims.get("jti", String.class);
            String userId = claims.getSubject();
            String storedUserId = redisTemplate.opsForValue().get("token:valid:" + jti);
            return userId != null && userId.equals(storedUserId);
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析令牌获取 Claims */
    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    /** 从令牌获取用户ID */
    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /** 从令牌获取 jti（令牌唯一标识） */
    public String getJti(String token) {
        return getClaims(token).get("jti", String.class);
    }

    /**
     * 失效单个令牌（登出时使用）
     * 删除 Redis 中 token:valid:{jti} 并从用户令牌集合中移除
     */
    public void invalidateToken(String token) {
        String jti = getJti(token);
        Long userId = getUserId(token);
        redisTemplate.delete("token:valid:" + jti);
        redisTemplate.opsForSet().remove("user:tokens:" + userId, jti);
    }

    /**
     * 失效用户所有令牌（修改密码、踢出用户时使用）
     * 遍历 user:tokens:{userId} 集合，批量删除对应 token:valid: 键
     */
    public void invalidateAllUserTokens(Long userId) {
        var jtis = redisTemplate.opsForSet().members("user:tokens:" + userId);
        if (jtis != null) {
            for (String jti : jtis) {
                redisTemplate.delete("token:valid:" + jti);
            }
        }
        redisTemplate.delete("user:tokens:" + userId);
    }

    /** 获取令牌过期时间（毫秒） */
    public long getExpiration() {
        return expiration;
    }
}
