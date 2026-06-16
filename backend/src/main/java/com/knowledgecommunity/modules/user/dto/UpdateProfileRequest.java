package com.knowledgecommunity.modules.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求 DTO
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "用户名最长50字符")
    private String username;

    /** 头像 URL */
    private String avatarUrl;

    /** 个性签名 */
    @Size(max = 200, message = "签名最长200字符")
    private String signature;
}
