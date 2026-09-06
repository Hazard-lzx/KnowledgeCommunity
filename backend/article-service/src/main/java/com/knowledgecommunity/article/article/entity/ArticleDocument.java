package com.knowledgecommunity.article.article.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

/**
 * 文章 Elasticsearch 文档实体（article-service 写入，search-service 读取）
 * 标题和内容使用 ik 分词器，支持中文搜索
 */
@Data
@Document(indexName = "article")
public class ArticleDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long userId;

    /** 标题，ik_max_word 索引分词，ik_smart 搜索分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    /** 内容，同标题分词策略 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Text)
    private String summary;

    @Field(type = FieldType.Keyword)
    private String coverUrl;

    @Field(type = FieldType.Integer)
    private Integer status;

    /** 标签数组，Keyword 类型用于精确过滤 */
    @Field(type = FieldType.Keyword)
    private String[] tags;

    @Field(type = FieldType.Integer)
    private Integer likeCount;

    @Field(type = FieldType.Integer)
    private Integer collectCount;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Date)
    private LocalDateTime createTime;
}
