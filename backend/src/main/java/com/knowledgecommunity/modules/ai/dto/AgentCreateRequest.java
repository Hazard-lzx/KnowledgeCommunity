package com.knowledgecommunity.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 创作请求 DTO
 */
@Data
public class AgentCreateRequest {

    /** 创作目标 */
    @NotBlank(message = "创作目标不能为空")
    private String goal;

    /** 风格要求（轻松/专业/简洁等） */
    private String style;

    /** 目标字数 */
    private Integer wordCount;
}
