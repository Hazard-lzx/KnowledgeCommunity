package com.knowledgecommunity.modules.ai.agent.tools;

import com.knowledgecommunity.modules.article.dto.ArticleCreateRequest;
import com.knowledgecommunity.modules.article.service.ArticleService;
import com.knowledgecommunity.security.UserPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Agent 工具：发布文章到社区
 */
public class PublishTool {

    private final ArticleService articleService;

    public PublishTool(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Tool(description = "将创作完成的文章发布到社区，需要提供标题、内容、摘要和标签，返回发布结果（含文章ID）")
    public String publishArticle(
            @ToolParam(description = "文章标题") String title,
            @ToolParam(description = "文章内容（Markdown格式）") String content,
            @ToolParam(description = "文章摘要") String summary,
            @ToolParam(description = "标签，逗号分隔") String tags) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal currentUser)) {
                return "发布失败：用户未登录";
            }

            ArticleCreateRequest request = new ArticleCreateRequest();
            request.setTitle(title);
            request.setContent(content);
            request.setTags(tags);
            request.setStatus(1);

            Long articleId = articleService.createArticle(currentUser, request);
            return "文章发布成功！文章ID：" + articleId + "，标题：" + title;
        } catch (Exception e) {
            return "发布失败：" + e.getMessage();
        }
    }
}
