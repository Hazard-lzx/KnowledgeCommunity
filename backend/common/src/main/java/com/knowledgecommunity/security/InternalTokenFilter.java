package com.knowledgecommunity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部接口令牌过滤器：保护 /api/internal/**
 * 仅供网关后的服务间调用（携带 X-Internal-Token 共享密钥），
 * 网关已对 /api/internal/** 整体封锁，外部无法经网关触达。
 */
@Component
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    @Value("${internal.token:}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader("X-Internal-Token");
        if (internalToken == null || internalToken.isBlank() || token == null || !token.equals(internalToken)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"内部接口令牌无效\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
