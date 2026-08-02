package com.knowledgecommunity.modules.ai.controller;

import com.knowledgecommunity.modules.ai.dto.AgentCreateRequest;
import com.knowledgecommunity.modules.ai.service.AgentService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 智能创作 Agent 控制器
 * POST /api/ai/agent/create → SSE 流式响应
 * 使用 AsyncContext + 直接写入 Response，确保 SSE 事件即时 flush
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(value = "/create", produces = "text/event-stream")
    public void create(@Valid @RequestBody AgentCreateRequest request,
                       HttpServletRequest httpRequest,
                       HttpServletResponse httpResponse) {
        httpResponse.setContentType("text/event-stream");
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("X-Accel-Buffering", "no"); // 禁用 Nginx 缓冲

        String sessionId = UUID.randomUUID().toString();
        SecurityContext securityContext = SecurityContextHolder.getContext();

        AsyncContext asyncContext = httpRequest.startAsync();
        asyncContext.setTimeout(600000); // 10 分钟超时

        new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                PrintWriter writer = asyncContext.getResponse().getWriter();
                agentService.execute(
                        request.getGoal(),
                        request.getStyle(),
                        request.getWordCount(),
                        sessionId,
                        writer
                );
                writer.flush();
            } catch (Exception e) {
                log.error("Agent执行异常, sessionId={}", sessionId, e);
            } finally {
                SecurityContextHolder.clearContext();
                asyncContext.complete();
            }
        }).start();
    }
}
