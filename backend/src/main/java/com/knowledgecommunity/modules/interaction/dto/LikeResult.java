package com.knowledgecommunity.modules.interaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 点赞/收藏操作结果 DTO
 */
@Data
@AllArgsConstructor
public class LikeResult {

    /** 操作是否生效（已点赞再点赞返回 false） */
    private boolean liked;

    /** 当前总计数 */
    private int count;
}
