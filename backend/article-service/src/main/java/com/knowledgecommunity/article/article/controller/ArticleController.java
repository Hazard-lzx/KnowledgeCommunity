package com.knowledgecommunity.article.article.controller;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.FeedItem;
import com.knowledgecommunity.article.article.dto.ArticleCreateRequest;
import com.knowledgecommunity.article.article.dto.ArticleDetailResponse;
import com.knowledgecommunity.article.article.service.ArticleService;
import com.knowledgecommunity.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章控制器：发布文章、获取文章详情、更新文章、删除文章
 */
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /** 发布/创建文章 */
    @PostMapping
    public Result<Long> createArticle(@AuthenticationPrincipal UserPrincipal currentUser,
                                      @Valid @RequestBody ArticleCreateRequest request) {
        return Result.success(articleService.createArticle(currentUser, request));
    }

    /** 获取文章详情（含点赞/收藏状态、Redis 计数，匿名用户也可访问） */
    @GetMapping("/{id}")
    public Result<ArticleDetailResponse> getArticleDetail(@PathVariable Long id,
                                                          @Nullable @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(articleService.getArticleDetail(id, currentUser));
    }

    /** 更新文章 */
    @PutMapping("/{id}")
    public Result<Void> updateArticle(@AuthenticationPrincipal UserPrincipal currentUser,
                                      @PathVariable Long id,
                                      @Valid @RequestBody ArticleCreateRequest request) {
        articleService.updateArticle(currentUser, id, request);
        return Result.success();
    }

    /** 删除文章 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@AuthenticationPrincipal UserPrincipal currentUser,
                                      @PathVariable Long id) {
        articleService.deleteArticle(currentUser, id);
        return Result.success();
    }

    /** 获取指定用户的文章列表（按状态过滤） */
    @GetMapping("/user/{userId}")
    public Result<List<FeedItem>> getUserArticles(@PathVariable Long userId,
                                                  @RequestParam(required = false) Integer status,
                                                  @Nullable @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(articleService.getUserArticles(userId, status, currentUser));
    }
}
