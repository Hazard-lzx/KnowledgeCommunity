package com.knowledgecommunity.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户文章统计（article-service 内部接口返回，供 auth-service 组装用户资料页）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserArticleStats {

    /** 已发布文章数 */
    private int articleCount;

    /** 获赞总数（所有已发布文章 like_count 之和） */
    private int likeCount;
}
