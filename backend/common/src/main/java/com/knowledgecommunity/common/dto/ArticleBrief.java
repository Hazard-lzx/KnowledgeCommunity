package com.knowledgecommunity.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章简要信息（article-service 内部接口返回，供 feed/search 拉取文章列表）
 * 不含作者用户名/头像（由调用方再向 auth-service 批量补全）
 */
@Data
public class ArticleBrief {

    private Long id;
    private Long userId;
    private String title;
    private String summary;
    private String coverUrl;
    private String[] tags;
    private Integer likeCount;
    private Integer collectCount;
    private Integer viewCount;
    private LocalDateTime createTime;
}
