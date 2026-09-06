package com.knowledgecommunity.article.article.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.ArticleBrief;
import com.knowledgecommunity.common.dto.UserArticleStats;
import com.knowledgecommunity.article.article.dto.ArticleCreateRequest;
import com.knowledgecommunity.article.article.dto.InternalArticleResponse;
import com.knowledgecommunity.article.article.entity.Article;
import com.knowledgecommunity.article.article.mapper.ArticleMapper;
import com.knowledgecommunity.article.article.service.ArticleService;
import com.knowledgecommunity.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 内部接口（仅供 ai-service / 服务间调用，经 InternalTokenFilter 校验 X-Internal-Token）
 * - GET  /api/internal/articles/{id}          拉取单篇文章（索引回源）
 * - GET  /api/internal/articles/published     分页拉取已发布文章（全量重建索引）
 * - POST /api/internal/articles/publish       代表用户发布文章（Agent 发布工具回调）
 * - GET  /api/internal/articles/stats/{userId}    用户文章统计（auth-service 用户资料页）
 * - GET  /api/internal/articles/batch?ids=        批量获取文章简要（search 补发布时间）
 * - GET  /api/internal/articles/page?cursor=&size=&userIds=  游标分页文章列表（feed）
 */
@RestController
@RequestMapping("/api/internal/articles")
@RequiredArgsConstructor
public class InternalArticleController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleMapper articleMapper;
    private final ArticleService articleService;

    @GetMapping("/{id}")
    public Result<InternalArticleResponse> getArticle(@PathVariable Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return Result.success(toResponse(article));
    }

    @GetMapping("/published")
    public Result<List<InternalArticleResponse>> listPublished(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .orderByAsc(Article::getId)
                        .last("LIMIT " + ((page - 1) * size) + ", " + size));
        return Result.success(articles.stream().map(this::toResponse).toList());
    }

    @PostMapping("/publish")
    public Result<Long> publish(@Valid @RequestBody InternalPublishRequest request,
                                @RequestHeader("X-User-Id") Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "缺少用户身份");
        }
        ArticleCreateRequest createRequest = new ArticleCreateRequest();
        createRequest.setTitle(request.getTitle());
        createRequest.setContent(request.getContent());
        createRequest.setTags(request.getTags());
        createRequest.setStatus(1);
        Long articleId = articleService.createArticle(new UserPrincipal(userId, "agent"), createRequest);
        return Result.success(articleId);
    }

    /** 用户文章统计：已发布文章数 + 获赞总数 */
    @GetMapping("/stats/{userId}")
    public Result<UserArticleStats> getUserArticleStats(@PathVariable Long userId) {
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getUserId, userId)
                        .eq(Article::getStatus, 1)
                        .select(Article::getId, Article::getLikeCount));
        int likeCount = articles.stream()
                .mapToInt(a -> a.getLikeCount() != null ? a.getLikeCount() : 0)
                .sum();
        return Result.success(new UserArticleStats(articles.size(), likeCount));
    }

    /** 批量获取文章简要信息（供 search-service 补全发布时间等 DB 字段） */
    @GetMapping("/batch")
    public Result<List<ArticleBrief>> batchArticles(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<ArticleBrief> briefs = articleMapper.selectBatchIds(ids).stream()
                .map(ArticleService::toBrief)
                .toList();
        return Result.success(briefs);
    }

    /**
     * 游标分页获取已发布文章（供 feed-service）
     * @param cursor  上一页最后一条的创建时间（yyyy-MM-dd HH:mm:ss，空表示首页）
     * @param userIds 关注模式下的作者ID列表（空表示全站）
     */
    @GetMapping("/page")
    public Result<List<ArticleBrief>> pageArticles(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) List<Long> userIds) {

        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1);
        if (userIds != null && !userIds.isEmpty()) {
            query.in(Article::getUserId, userIds);
        }
        if (StringUtils.isNotBlank(cursor)) {
            query.lt(Article::getCreateTime, LocalDateTime.parse(cursor, FORMATTER));
        }
        query.orderByDesc(Article::getCreateTime).last("LIMIT " + size);

        List<ArticleBrief> briefs = articleMapper.selectList(query).stream()
                .map(ArticleService::toBrief)
                .toList();
        return Result.success(briefs);
    }

    private InternalArticleResponse toResponse(Article article) {
        InternalArticleResponse response = new InternalArticleResponse();
        response.setId(article.getId());
        response.setUserId(article.getUserId());
        response.setTitle(article.getTitle());
        response.setContent(article.getContent());
        response.setSummary(article.getSummary());
        response.setTags(StringUtils.isNotBlank(article.getTags())
                ? List.of(article.getTags().split(",")) : List.of());
        response.setStatus(article.getStatus());
        return response;
    }

    @Data
    public static class InternalPublishRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
        @NotBlank(message = "内容不能为空")
        private String content;
        private List<String> tags;
    }
}
