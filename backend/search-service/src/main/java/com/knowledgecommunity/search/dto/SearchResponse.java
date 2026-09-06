package com.knowledgecommunity.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 搜索响应 DTO，支持 search_after 深分页
 */
@Data
@AllArgsConstructor
public class SearchResponse {

    /** 搜索结果列表 */
    private List<SearchHit> hits;

    /** 下一页游标（base64 编码的 score） */
    private String searchAfter;

    /** 是否有更多结果 */
    private boolean hasMore;
}
