package com.knowledgecommunity.modules.ai.agent.tools;

import com.knowledgecommunity.modules.article.dto.ArticleCreateRequest;
import com.knowledgecommunity.modules.article.service.ArticleService;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 工具：发布文章到社区
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishTool {

    private final ArticleService articleService;

    @Tool(description = "发布文章到社区")
    public String publishArticle(
            @ToolParam(description = "文章标题") String title,
            @ToolParam(description = "文章内容") String content,
            @ToolParam(description = "文章摘要") String summary,
            @ToolParam(description = "文章标签") String tags) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal currentUser)) {
                return "发布失败：用户未登录";
            }

            ArticleCreateRequest request = new ArticleCreateRequest();
            request.setTitle(title);
            request.setContent(content);
            request.setTags(tags != null ? List.of(tags) : null);
            request.setStatus(1);

            Long articleId = articleService.createArticle(currentUser, request);
            return "文章发布成功！文章ID：" + articleId + "，标题：" + title;
        } catch (Exception e) {
            log.error("发布文章失败", e);
            return "发布失败：" + e.getMessage();
        }
    }
}