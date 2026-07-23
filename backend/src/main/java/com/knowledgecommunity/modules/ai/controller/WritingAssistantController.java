package com.knowledgecommunity.modules.ai.controller;

import com.knowledgecommunity.modules.ai.dto.WritingAssistRequest;
import com.knowledgecommunity.modules.ai.service.WritingAssistantService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class WritingAssistantController {

    private final WritingAssistantService writingAssistantService;

    @PostMapping("/writing-assist")
    public void writingAssist(@Valid @RequestBody WritingAssistRequest request,
                              HttpServletResponse response) {
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            PrintWriter writer = response.getWriter();
            writingAssistantService.assist(
                    request.getType(), request.getContent(), request.getContext(), writer);
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}