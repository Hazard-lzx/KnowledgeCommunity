package com.knowledgecommunity.search.service;

import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.ArticleBrief;
import com.knowledgecommunity.common.dto.UserBrief;
import com.knowledgecommunity.search.client.ArticleClient;
import com.knowledgecommunity.search.client.AuthClient;
import com.knowledgecommunity.search.dto.SearchHit;
import com.knowledgecommunity.search.dto.SearchResponse;
import com.knowledgecommunity.search.entity.ArticleDocument;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilterBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索服务：基于 Elasticsearch 实现文章搜索与联想建议
 * - bool 查询组合 match + term 过滤
 * - function_score 融合 BM25 与 like_count（field_value_factor）
 * - search_after 深分页
 * - 作者信息经 Feign 调 auth-service；发布时间经 Feign 调 article-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final AuthClient authClient;
    private final ArticleClient articleClient;
    private final StringRedisTemplate redisTemplate;

    /**
     * 搜索文章
     */
    public SearchResponse search(String keyword, String tag, int size, String searchAfter, UserPrincipal currentUser) {
        List<Query> mustQueries = new ArrayList<>();

        if (StringUtils.isNotBlank(keyword)) {
            // 标题：match 分词匹配 OR wildcard 子串匹配，取并集
            String wildcardValue = "*" + keyword.toLowerCase() + "*";
            Query matchTitle = Query.of(q -> q.match(m -> m.field("title").query(FieldValue.of(keyword))));
            Query wildcardTitle = Query.of(q -> q.wildcard(w -> w.field("title").value(wildcardValue).caseInsensitive(true)));
            Query titleQuery = Query.of(q -> q.bool(b -> b
                    .should(List.of(matchTitle, wildcardTitle))
                    .minimumShouldMatch("1")));
            Query matchContent = Query.of(q -> q.match(m -> m.field("content").query(FieldValue.of(keyword))));
            mustQueries.add(Query.of(q -> q.disMax(d -> d.queries(List.of(titleQuery, matchContent)).tieBreaker(0.3))));
        }

        if (StringUtils.isNotBlank(tag)) {
            Query termTag = Query.of(q -> q.term(t -> t.field("tags").value(FieldValue.of(tag))));
            mustQueries.add(termTag);
        }

        // 只搜索已发布的文章
        Query statusFilter = Query.of(q -> q.term(t -> t.field("status").value(FieldValue.of(1))));

        BoolQuery boolQuery = BoolQuery.of(b -> {
            if (!mustQueries.isEmpty()) {
                b.must(mustQueries);
            }
            b.filter(List.of(statusFilter));
            return b;
        });

        // function_score: BM25 + like_count 的 log1p 加权
        Query functionScoreQuery = Query.of(q -> q.functionScore(fs -> fs
                .query(Query.of(qb -> qb.bool(boolQuery)))
                .functions(f -> f
                        .fieldValueFactor(fv -> fv.field("likeCount").factor(0.1).modifier(FieldValueFactorModifier.Log1p))
                )
                .scoreMode(FunctionScoreMode.Sum)
                .boostMode(FunctionBoostMode.Replace)
        ));

        NativeQuery nativeQuery = buildNativeQuery(functionScoreQuery, size, null, null);

        if (StringUtils.isNotBlank(searchAfter)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(searchAfter));
                String[] parts = decoded.split("\\|");
                double score = Double.parseDouble(parts[0]);
                Long lastId = parts.length > 1 ? Long.parseLong(parts[1]) : null;
                nativeQuery = buildNativeQuery(functionScoreQuery, size, score, lastId);
            } catch (Exception e) {
                log.warn("searchAfter 解析失败: {}", searchAfter, e);
            }
        }

        SearchHits<ArticleDocument> searchHits = elasticsearchTemplate.search(nativeQuery, ArticleDocument.class);

        List<SearchHit> hits = searchHits.getSearchHits().stream()
                .limit(size)
                .map(this::toSearchHit)
                .collect(Collectors.toList());

        // 批量补充用户信息（Feign）+ 从 Redis 读取实时计数 + 发布时间（Feign）
        if (!hits.isEmpty()) {
            Map<Long, UserBrief> userMap = fetchUsers(
                    hits.stream().map(SearchHit::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
            Map<Long, ArticleBrief> articleMap = fetchArticles(
                    hits.stream().map(SearchHit::getId).collect(Collectors.toSet()));

            for (SearchHit hit : hits) {
                UserBrief user = userMap.get(hit.getUserId());
                if (user != null) {
                    hit.setUsername(user.getUsername());
                    hit.setAvatarUrl(user.getAvatarUrl());
                }
                ArticleBrief article = articleMap.get(hit.getId());
                if (article != null) {
                    hit.setCreateTime(article.getCreateTime());
                }
                // 从 Redis 读取实时点赞/收藏/浏览计数
                String likeVal = redisTemplate.opsForValue().get("article:likes:" + hit.getId());
                String collectVal = redisTemplate.opsForValue().get("article:collects:" + hit.getId());
                String viewVal = redisTemplate.opsForValue().get("article:views:" + hit.getId());
                if (likeVal != null) hit.setLikeCount(Integer.parseInt(likeVal));
                if (collectVal != null) hit.setCollectCount(Integer.parseInt(collectVal));
                if (viewVal != null) hit.setViewCount(Integer.parseInt(viewVal));
            }
        }

        // 已登录用户：批量查询点赞/收藏状态
        if (currentUser != null && !hits.isEmpty()) {
            Long userId = currentUser.getUserId();
            for (SearchHit hit : hits) {
                hit.setLiked(redisTemplate.opsForValue().getBit("article:like:bitmap:" + hit.getId(), userId));
                hit.setCollected(redisTemplate.opsForValue().getBit("article:collect:bitmap:" + hit.getId(), userId));
            }
        }

        boolean hasMore = searchHits.getSearchHits().size() > size;
        String nextSearchAfter = null;
        if (hasMore) {
            org.springframework.data.elasticsearch.core.SearchHit<ArticleDocument> lastHit =
                    searchHits.getSearchHits().get(size - 1);
            String cursorValue = lastHit.getScore() + "|" + lastHit.getContent().getId();
            nextSearchAfter = Base64.getEncoder().encodeToString(cursorValue.getBytes());
        }

        return new SearchResponse(hits, nextSearchAfter, hasMore);
    }

    /** 经 Feign 批量获取用户信息；失败降级为空（搜索结果不带作者信息） */
    private Map<Long, UserBrief> fetchUsers(Set<Long> userIds) {
        try {
            Result<List<UserBrief>> result = authClient.batchUsers(new ArrayList<>(userIds));
            if (result.getCode() == 200 && result.getData() != null) {
                return result.getData().stream().collect(Collectors.toMap(UserBrief::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败（搜索结果将缺少作者信息）", e);
        }
        return Collections.emptyMap();
    }

    /** 经 Feign 批量获取文章简要信息；失败降级为空 */
    private Map<Long, ArticleBrief> fetchArticles(Set<Long> articleIds) {
        try {
            Result<List<ArticleBrief>> result = articleClient.batchArticles(new ArrayList<>(articleIds));
            if (result.getCode() == 200 && result.getData() != null) {
                return result.getData().stream().collect(Collectors.toMap(ArticleBrief::getId, a -> a));
            }
        } catch (Exception e) {
            log.warn("批量获取文章信息失败（搜索结果将缺少发布时间）", e);
        }
        return Collections.emptyMap();
    }

    /**
     * 标题联想建议（前缀匹配）
     */
    public List<String> suggest(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return Collections.emptyList();
        }

        NativeQuery suggestQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.prefix(p -> p.field("title").value(prefix))))
                .withMaxResults(10)
                .withSourceFilter(new FetchSourceFilterBuilder().withIncludes("title").build())
                .build();

        SearchHits<ArticleDocument> searchHits = elasticsearchTemplate.search(suggestQuery, ArticleDocument.class);

        return searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent().getTitle())
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 构建 NativeQuery
     */
    private NativeQuery buildNativeQuery(Query query, int size, Double scoreAfter, Long idAfter) {
        var builder = NativeQuery.builder()
                .withQuery(query)
                .withSort(Sort.by(Sort.Order.desc("_score"), Sort.Order.desc("id")))
                .withMaxResults(size + 1);
        if (scoreAfter != null) {
            List<Object> searchAfterValues = new ArrayList<>();
            searchAfterValues.add(scoreAfter);
            if (idAfter != null) {
                searchAfterValues.add(idAfter);
            }
            builder.withSearchAfter(searchAfterValues);
        }
        return builder.build();
    }

    /**
     * ES SearchHit 转换为业务 SearchHit DTO
     */
    private SearchHit toSearchHit(org.springframework.data.elasticsearch.core.SearchHit<ArticleDocument> hit) {
        ArticleDocument doc = hit.getContent();
        SearchHit searchHit = new SearchHit();
        searchHit.setId(doc.getId());
        searchHit.setUserId(doc.getUserId());
        searchHit.setTitle(doc.getTitle());
        searchHit.setSummary(doc.getSummary());
        searchHit.setCoverUrl(doc.getCoverUrl());
        searchHit.setTags(doc.getTags());
        searchHit.setLikeCount(doc.getLikeCount());
        searchHit.setCollectCount(doc.getCollectCount());
        searchHit.setViewCount(doc.getViewCount());
        searchHit.setCreateTime(doc.getCreateTime());
        searchHit.setScore((double) hit.getScore());
        return searchHit;
    }
}
