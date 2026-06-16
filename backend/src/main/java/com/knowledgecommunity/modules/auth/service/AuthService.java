package com.knowledgecommunity.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.modules.auth.dto.LoginRequest;
import com.knowledgecommunity.modules.auth.dto.LoginResponse;
import com.knowledgecommunity.modules.auth.dto.RegisterRequest;
import com.knowledgecommunity.modules.auth.entity.User;
import com.knowledgecommunity.modules.auth.mapper.UserMapper;
import com.knowledgecommunity.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：登录、注册、登出
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 登录：校验用户名密码，生成 JWT 令牌
     */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername());
        return new LoginResponse(token, jwtTokenProvider.getExpiration() / 1000);
    }

    /**
     * 注册：校验用户名唯一性，加密密码后入库
     */
    public void register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        userMapper.insert(user);
    }

    /**
     * 登出：将令牌从 Redis 白名单移除
     */
    public void logout(String token) {
        jwtTokenProvider.invalidateToken(token);
    }
}
