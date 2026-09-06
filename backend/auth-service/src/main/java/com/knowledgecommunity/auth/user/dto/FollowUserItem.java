package com.knowledgecommunity.auth.user.dto;

import lombok.Data;

/**
 * 关注/粉丝列表项 DTO
 */
@Data
public class FollowUserItem {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 头像URL */
    private String avatarUrl;

    /** 当前登录用户是否已关注该用户 */
    private Boolean followed;
}
