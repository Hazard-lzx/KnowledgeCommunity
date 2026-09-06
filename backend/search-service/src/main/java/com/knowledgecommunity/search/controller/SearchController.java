package com.knowledgecommunity.search.controller;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.search.dto.SearchResponse;
import com.knowledgecommunity.search.service.SearchService;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索控制器：文章搜索 + 联想建议
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** 搜索文章（支持关键词 + 标签过滤 + search_after 深分页） */
    @GetMapping
    public Result<SearchResponse> search(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String tag,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String searchAfter,
                                         @AuthenticationPrincipal UserPrincipal currentUser) {
        return Result.success(searchService.search(keyword, tag, size, searchAfter, currentUser));
    }

    /** 标题联想建议（前缀匹配） */
    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam String prefix) {
        return Result.success(searchService.suggest(prefix));
    }
}
