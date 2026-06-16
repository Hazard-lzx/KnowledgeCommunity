package com.knowledgecommunity.modules.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Feed 流响应 DTO，支持游标分页
 */
@Data
@AllArgsConstructor
public class FeedResponse {

    /** Feed 条目列表 */
    private List<FeedItem> items;

    /** 下一页游标（文章创建时间） */
    private String nextCursor;

    /** 是否有更多数据 */
    private boolean hasMore;
}
