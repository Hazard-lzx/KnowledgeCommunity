package com.knowledgecommunity.modules.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体，对应 article 表
 * status: 0=草稿, 1=已发布
 */
@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者ID */
    private Long userId;

    /** 标题 */
    private String title;

    /** Markdown 内容 */
    private String content;

    /** AI 生成的摘要 */
    private String summary;

    /** 封面图 URL */
    private String coverUrl;

    /** 状态：0=草稿, 1=已发布 */
    private Integer status;

    /** 标签（逗号分隔） */
    private String tags;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer collectCount;

    /** 浏览数 */
    private Integer viewCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
