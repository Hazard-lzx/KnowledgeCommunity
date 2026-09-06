package com.knowledgecommunity.auth.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关注事件载荷，序列化为 JSON 存入 Outbox 表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowEventPayload {

    /** 关注者ID */
    private Long followerId;

    /** 被关注者ID */
    private Long followeeId;
}
