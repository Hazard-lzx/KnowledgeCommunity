package com.knowledgecommunity.modules.auth.controller;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.modules.auth.dto.LoginRequest;
import com.knowledgecommunity.modules.auth.dto.LoginResponse;
import com.knowledgecommunity.modules.auth.dto.RegisterRequest;
import com.knowledgecommunity.modules.auth.service.AuthService;
import com.knowledgecommunity.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器：登录、注册、登出
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 登录：校验用户名密码，返回 JWT 令牌 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 注册：创建新用户 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /** 登出：将当前令牌从 Redis 白名单移除 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.substring(7);
        authService.logout(token);
        return Result.success();
    }
}
