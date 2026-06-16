package com.knowledgecommunity.modules.feed.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Feed 流条目 DTO，包含文章摘要和作者信息
 */
@Data
public class FeedItem {

    private Long id;
    private Long userId;

    /** 作者用户名 */
    private String username;

    /** 作者头像 */
    private String avatarUrl;

    private String title;

    /** AI 摘要 */
    private String summary;

    private String coverUrl;
    private String[] tags;
    private Integer likeCount;
    private Integer collectCount;
    private Integer viewCount;
    /** 当前用户是否已点赞 */
    private Boolean liked;
    /** 当前用户是否已收藏 */
    private Boolean collected;
    private LocalDateTime createTime;
}
