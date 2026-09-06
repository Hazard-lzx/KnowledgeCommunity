package com.knowledgecommunity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 网关身份头过滤器（新服务专用，替代单体的 JwtAuthenticationFilter）
 *
 * 新拆出的服务不解析 JWT：网关 AuthGlobalFilter 已完成 JWT 验签 + Redis 白名单校验，
 * 并注入可信的 X-User-Id / X-Username 身份头，服务侧只信这两（前提：服务只绑内网地址）。
 * 过滤器将身份头转为 Authentication 放入 SecurityContext，
 * 使 @AuthenticationPrincipal UserPrincipal 写法在新服务中继续可用。
 */
@Component
@RequiredArgsConstructor
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        String username = request.getHeader(HEADER_USERNAME);
        if (userId != null && userId.matches("\\d+")) {
            UserPrincipal principal = new UserPrincipal(Long.valueOf(userId), username);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
