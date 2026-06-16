package com.knowledgecommunity.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 关注事件载荷，序列化为 JSON 存入 Outbox 表
 */
@Data
@AllArgsConstructor
public class FollowEventPayload {

    /** 关注者ID */
    private Long followerId;

    /** 被关注者ID */
    private Long followeeId;
}
