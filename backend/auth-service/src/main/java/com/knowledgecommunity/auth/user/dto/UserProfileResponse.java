package com.knowledgecommunity.auth.user.dto;

import lombok.Data;

/**
 * 用户资料响应 DTO
 */
@Data
public class UserProfileResponse {

    private Long id;
    private String username;
    private String avatarUrl;
    private String signature;

    /** 关注数 */
    private Integer followingCount;

    /** 粉丝数 */
    private Integer followerCount;

    /** 文章数（由 article-service 内部接口提供） */
    private Integer articleCount;

    /** 获赞总数（由 article-service 内部接口提供） */
    private Integer likeCount;

    /** 当前登录用户是否已关注该用户 */
    private Boolean followed;
}
