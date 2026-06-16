package com.knowledgecommunity.modules.search.service;

import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.entity.ArticleDocument;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import com.knowledgecommunity.modules.auth.entity.User;
import com.knowledgecommunity.modules.auth.mapper.UserMapper;
import com.knowledgecommunity.modules.search.dto.SearchHit;
import com.knowledgecommunity.modules.search.dto.SearchResponse;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 搜索文章
     * @param keyword   关键词（match title + content）
     * @param tag       标签过滤（term tags）
     * @param size      每页条数
     * @param searchAfter  上一页最后一条的排序值（base64 编码的 score）
     * @return 搜索结果
     */
    public SearchResponse search(String keyword, String tag, int size, String searchAfter, UserPrincipal currentUser) {
        // 构建 must 查询条件
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
            // 标签精确匹配
            Query termTag = Query.of(q -> q.term(t -> t.field("tags").value(FieldValue.of(tag))));
            mustQueries.add(termTag);
        }

        // 只搜索已发布的文章
        Query statusFilter = Query.of(q -> q.term(t -> t.field("status").value(FieldValue.of(1))));

        // 组装 bool 查询
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

        // 构建 NativeQuery，多取一条用于判断是否有下一页
        NativeQuery nativeQuery = buildNativeQuery(functionScoreQuery, size, null, null);

        // 如果有 searchAfter 参数，解析并设置
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

        // 执行搜索
        SearchHits<ArticleDocument> searchHits = elasticsearchTemplate.search(nativeQuery, ArticleDocument.class);

        // 转换结果，限制为 size 条
        List<SearchHit> hits = searchHits.getSearchHits().stream()
                .limit(size)
                .map(this::toSearchHit)
                .collect(Collectors.toList());

        // 批量补充用户信息 + 从 Redis 读取实时计数 + 从 DB 读取发布时间
        if (!hits.isEmpty()) {
            Set<Long> userIds = hits.stream().map(SearchHit::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            List<User> users = userMapper.selectBatchIds(userIds);
            Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

            Set<Long> articleIds = hits.stream().map(SearchHit::getId).collect(Collectors.toSet());
            List<Article> articles = articleMapper.selectBatchIds(articleIds);
            Map<Long, Article> articleMap = articles.stream().collect(Collectors.toMap(Article::getId, a -> a));

            for (SearchHit hit : hits) {
                User user = userMap.get(hit.getUserId());
                if (user != null) {
                    hit.setUsername(user.getUsername());
                    hit.setAvatarUrl(user.getAvatarUrl());
                }
                // 从 DB 读取发布时间
                Article article = articleMap.get(hit.getId());
                if (article != null) {
                    hit.setCreateTime(article.getCreateTime());
                }
                // 从 Redis 读取实时点赞/收藏/浏览计数
                String likeKey = "article:likes:" + hit.getId();
                String collectKey = "article:collects:" + hit.getId();
                String viewKey = "article:views:" + hit.getId();
                String likeVal = redisTemplate.opsForValue().get(likeKey);
                String collectVal = redisTemplate.opsForValue().get(collectKey);
                String viewVal = redisTemplate.opsForValue().get(viewKey);
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

        // 判断是否有更多结果
        boolean hasMore = searchHits.getSearchHits().size() > size;
        String nextSearchAfter = null;
        if (hasMore) {
            // 取第 size 条的 score 和 id 作为下一页游标
            org.springframework.data.elasticsearch.core.SearchHit<ArticleDocument> lastHit =
                    searchHits.getSearchHits().get(size - 1);
            String cursorValue = lastHit.getScore() + "|" + lastHit.getContent().getId();
            nextSearchAfter = Base64.getEncoder().encodeToString(cursorValue.getBytes());
        }

        return new SearchResponse(hits, nextSearchAfter, hasMore);
    }

    /**
     * 标题联想建议（前缀匹配）
     * @param prefix 前缀字符串
     * @return 匹配的标题列表
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
     * @param query       查询条件
     * @param size        每页条数
     * @param scoreAfter  上一页最后一条的 score（null 表示首页）
     * @param idAfter     上一页最后一条的 id（用于 score 相同时的去重）
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
