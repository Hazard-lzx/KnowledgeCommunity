package com.knowledgecommunity.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证用户主体，存储在 SecurityContext 中的用户信息
 * 通过 SecurityContextHolder.getContext().getAuthentication().getPrincipal() 获取
 */
@Data
@AllArgsConstructor
public class UserPrincipal {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;
}
