package com.knowledgecommunity.modules.article.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建文章请求 DTO
 */
@Data
public class ArticleCreateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    /** 封面图 URL */
    private String coverUrl;

    /** 标签列表 */
    private List<String> tags;

    /** 状态：0=草稿, 1=已发布，默认已发布 */
    private Integer status = 1;
}
