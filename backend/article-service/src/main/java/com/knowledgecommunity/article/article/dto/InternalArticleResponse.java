package com.knowledgecommunity.article.article.dto;

import lombok.Data;

import java.util.List;

/** 内部文章数据（供 ai-service 索引回源 / 存量回填） */
@Data
public class InternalArticleResponse {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String summary;
    private List<String> tags;
    private Integer status;
}
