package com.knowledgecommunity.auth.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO，包含 JWT 令牌和过期时间
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    /** JWT 访问令牌 */
    private String accessToken;

    /** 令牌过期时间（秒） */
    private long expiresIn;
}
