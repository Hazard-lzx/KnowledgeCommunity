package com.knowledgecommunity.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件发件箱实体，对应 event_outbox 表
 * Outbox 模式：业务操作与事件记录在同一事务，由定时任务扫描投递 MQ
 * status: 0=待投递, 1=已投递, 2=投递失败
 */
@Data
@TableName("event_outbox")
public class EventOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 聚合类型（如 USER_RELATION） */
    private String aggregateType;

    /** 聚合ID（如 followerId:followeeId） */
    private String aggregateId;

    /** 事件类型（FOLLOWED / UNFOLLOWED） */
    private String eventType;

    /** 事件载荷 JSON */
    private String payload;

    /** 状态：0=待投递, 1=已投递, 2=投递失败 */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    private LocalDateTime createTime;
}
