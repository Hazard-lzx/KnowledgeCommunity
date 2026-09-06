package com.knowledgecommunity.common.dto;

import lombok.Data;

/**
 * 用户简要信息（auth-service 内部接口返回，供 article/search/feed 补全作者信息）
 */
@Data
public class UserBrief {

    private Long id;
    private String username;
    private String avatarUrl;
}
