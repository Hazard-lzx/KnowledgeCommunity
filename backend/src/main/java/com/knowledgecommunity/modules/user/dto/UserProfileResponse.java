package com.knowledgecommunity.modules.user.dto;

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

    /** 文章数 */
    private Integer articleCount;

    /** 获赞总数 */
    private Integer likeCount;

    /** 当前登录用户是否已关注该用户 */
    private Boolean followed;
}
