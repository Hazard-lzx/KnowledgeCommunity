package com.knowledgecommunity.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * - 继承 OncePerRequestFilter，每次请求只执行一次
 * - 从 Authorization: Bearer <token> 提取令牌
 * - 先 validateToken 校验签名和过期，再 isTokenValid 检查 Redis 白名单
 * - 通过后设置 SecurityContext，否则返回 401
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头提取 Bearer Token
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            // 先校验签名和过期，再检查 Redis 白名单
            if (jwtTokenProvider.validateToken(token) && jwtTokenProvider.isTokenValid(token)) {
                Claims claims = jwtTokenProvider.getClaims(token);
                Long userId = Long.parseLong(claims.getSubject());
                String username = claims.get("username", String.class);

                // 构建认证对象并设置到 SecurityContext
                UserPrincipal principal = new UserPrincipal(userId, username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // token 无效时不直接返回 401，交给 Spring Security 权限判断
            // 公开端点（permitAll）可以继续访问，受保护端点会返回 401/403
        }

        filterChain.doFilter(request, response);
    }

    /** 从 Authorization 请求头提取 Bearer Token */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
