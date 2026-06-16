package com.knowledgecommunity.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 问答请求 DTO
 */
@Data
public class QaRequest {

    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
