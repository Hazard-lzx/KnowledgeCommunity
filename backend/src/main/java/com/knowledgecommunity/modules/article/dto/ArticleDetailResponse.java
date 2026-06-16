package com.knowledgecommunity.modules.article.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章详情响应 DTO
 */
@Data
public class ArticleDetailResponse {

    private Long id;
    private Long userId;

    /** 作者用户名 */
    private String username;

    /** 作者头像 */
    private String avatarUrl;

    private String title;
    private String content;

    /** AI 摘要 */
    private String summary;

    private String coverUrl;
    private Integer status;
    private String[] tags;
    private Integer likeCount;
    private Integer collectCount;
    private Integer viewCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 当前用户是否已收藏 */
    private Boolean collected;
}
