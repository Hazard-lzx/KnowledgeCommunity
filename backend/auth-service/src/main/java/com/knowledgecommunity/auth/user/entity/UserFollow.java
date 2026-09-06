package com.knowledgecommunity.auth.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关注关系实体，对应 user_follow 表
 * status: 1=关注, 0=取关（软删除）
 */
@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者ID */
    private Long followerId;

    /** 被关注者ID */
    private Long followeeId;

    /** 状态：1=关注, 0=取关 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
