package com.knowledgecommunity.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 user 表
 */
@Data
@TableName("user")
public class User {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，唯一 */
    private String username;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatarUrl;

    /** 个性签名 */
    private String signature;

    /** 注册时间 */
    private LocalDateTime createTime;
}
