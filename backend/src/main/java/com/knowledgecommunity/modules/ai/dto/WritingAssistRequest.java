package com.knowledgecommunity.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 写作助手请求 DTO
 */
@Data
public class WritingAssistRequest {

    /** 类型：continue(续写) / polish(润色) / outline(大纲) */
    @NotBlank(message = "类型不能为空")
    private String type;

    /** 选中文本或标题 */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 可选，文章上下文 */
    private String context;
}
