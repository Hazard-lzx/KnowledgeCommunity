package com.knowledgecommunity.modules.ai.controller;

import com.knowledgecommunity.modules.ai.dto.QaRequest;
import com.knowledgecommunity.modules.ai.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 问答控制器：基于文章内容的 RAG 问答
 * 返回 SSE 流式响应
 */
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** 提问，返回 SSE 流式响应 */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody QaRequest request) {
        return aiService.ask(request.getArticleId(), request.getQuestion());
    }
}
