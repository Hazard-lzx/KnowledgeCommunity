package com.knowledgecommunity.feed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.ArticleBrief;
import com.knowledgecommunity.common.dto.FeedItem;
import com.knowledgecommunity.common.dto.UserBrief;
import com.knowledgecommunity.feed.client.ArticleClient;
import com.knowledgecommunity.feed.client.AuthClient;
import com.knowledgecommunity.feed.dto.FeedResponse;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Feed 服务：三级缓存 + 热点探测 + 单飞锁
 *
 * 缓存层级：
 * - L1 Caffeine：本地内存缓存，命中最快，容量有限
 * - L2 Redis 页面缓存：Feed 页面级缓存，30秒过期
 * - L3 Redis 文章片段缓存：单篇文章 FeedItem 缓存，60秒过期，热点自动延长
 *
 * 数据来源（拆分后不直连 MySQL）：
 * - 文章列表：article-service 内部接口 /api/internal/articles/page
 * - 关注关系：auth-service 内部接口 /api/internal/users/{id}/following-ids
 * - 作者信息：auth-service 内部接口 /api/internal/users/batch
 * - 计数/点赞位图：共享 Redis 直读
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final AuthClient authClient;
    private final ArticleClient articleClient;
    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    /** 单飞锁映射，防止缓存击穿时大量并发请求打到下游 */
    private final ConcurrentHashMap<String, ReentrantLock> flightLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取 Feed 流
     * 缓存查询顺序：L1 Caffeine → L2 Redis 页面 → 单飞锁 + 下游聚合
     * mode=all：展示全站最新文章
     * mode=following：仅展示关注用户的文章（需登录）
     */
    public FeedResponse getFeed(UserPrincipal currentUser, String cursor, int size, String mode) {
        Long userId = currentUser != null ? currentUser.getUserId() : 0L;
        String cacheKey = "feed:" + userId + ":" + mode + ":" + (cursor != null ? cursor : "latest");

        // L1 Caffeine 缓存
        Cache l1Cache = cacheManager.getCache("feed");
        if (l1Cache != null) {
            String cached = l1Cache.get(cacheKey, String.class);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, FeedResponse.class);
                } catch (JsonProcessingException e) {
                    log.warn("L1缓存反序列化失败", e);
                }
            }
        }

        // L2 Redis 页面缓存
        String l2Cached = redisTemplate.opsForValue().get(cacheKey);
        if (l2Cached != null) {
            try {
                FeedResponse response = objectMapper.readValue(l2Cached, FeedResponse.class);
                // 回填 L1
                if (l1Cache != null) {
                    l1Cache.put(cacheKey, l2Cached);
                }
                return response;
            } catch (JsonProcessingException e) {
                log.warn("L2缓存反序列化失败", e);
            }
        }

        // 单飞锁：同一页面缓存未命中时只有一个线程查询下游
        ReentrantLock lock = flightLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // double check：获取锁后再次检查 L2 缓存
            String l2DoubleCheck = redisTemplate.opsForValue().get(cacheKey);
            if (l2DoubleCheck != null) {
                try {
                    FeedResponse response = objectMapper.readValue(l2DoubleCheck, FeedResponse.class);
                    if (l1Cache != null) {
                        l1Cache.put(cacheKey, l2DoubleCheck);
                    }
                    return response;
                } catch (JsonProcessingException e) {
                    log.warn("L2缓存二次检查反序列化失败", e);
                }
            }

            // 查询下游服务
            FeedResponse response = queryFeedFromServices(currentUser, cursor, size, mode);

            // 写入 L1 + L2 缓存
            try {
                String json = objectMapper.writeValueAsString(response);
                if (l1Cache != null) {
                    l1Cache.put(cacheKey, json);
                }
                redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.SECONDS);
            } catch (JsonProcessingException e) {
                log.warn("Feed缓存序列化失败", e);
            }

            return response;
        } finally {
            lock.unlock();
            flightLocks.remove(cacheKey);
        }
    }

    /**
     * 从下游服务聚合 Feed 数据
     * mode=all：查询全站最新文章（忽略关注关系）
     * mode=following：查询关注用户的已发布文章（游标分页），未登录则返回空
     */
    private FeedResponse queryFeedFromServices(UserPrincipal currentUser, String cursor, int size, String mode) {
        List<Long> followeeIds = null;

        if ("following".equals(mode)) {
            // 关注模式：必须登录
            if (currentUser == null) {
                return new FeedResponse(Collections.emptyList(), null, false);
            }

            List<Long> ids = fetchFollowingIds(currentUser.getUserId());
            if (ids == null || ids.isEmpty()) {
                return new FeedResponse(Collections.emptyList(), null, false);
            }
            followeeIds = ids;
        }

        // 查询文章（游标分页，多取一条判断是否有下一页）
        List<ArticleBrief> articles = fetchArticlePage(cursor, size + 1, followeeIds);
        if (articles == null) {
            articles = Collections.emptyList();
        }

        boolean hasMore = articles.size() > size;
        if (hasMore) {
            articles = articles.subList(0, size);
        }

        // 批量获取作者信息
        Map<Long, UserBrief> userMap = new HashMap<>();
        if (!articles.isEmpty()) {
            List<Long> userIds = articles.stream().map(ArticleBrief::getUserId).distinct().collect(Collectors.toList());
            userMap.putAll(fetchUsers(userIds));
        }

        // 组装 FeedItem，尝试从 L3 Redis 文章片段缓存获取
        List<FeedItem> items = articles.stream()
                .map(article -> getFeedItemFromL3OrBuild(article, userMap))
                .collect(Collectors.toList());

        // 已登录用户：批量查询点赞/收藏状态
        if (currentUser != null && !items.isEmpty()) {
            Long userId = currentUser.getUserId();
            for (FeedItem item : items) {
                item.setLiked(redisTemplate.opsForValue().getBit("article:like:bitmap:" + item.getId(), userId));
                item.setCollected(redisTemplate.opsForValue().getBit("article:collect:bitmap:" + item.getId(), userId));
            }
        }

        // 下一页游标为当前页最后一条的创建时间
        String nextCursor = null;
        if (hasMore && !articles.isEmpty()) {
            nextCursor = articles.get(articles.size() - 1).getCreateTime().format(FORMATTER);
        }

        return new FeedResponse(items, nextCursor, hasMore);
    }

    /** 经 Feign 获取关注ID列表；失败抛业务异常由全局处理器返回（关注模式无法降级） */
    private List<Long> fetchFollowingIds(Long userId) {
        try {
            Result<List<Long>> result = authClient.followingIds(userId);
            if (result.getCode() == 200) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("获取关注列表失败: userId={}", userId, e);
        }
        return Collections.emptyList();
    }

    /** 经 Feign 获取文章分页；失败降级为空列表 */
    private List<ArticleBrief> fetchArticlePage(String cursor, int size, List<Long> userIds) {
        try {
            Result<List<ArticleBrief>> result = articleClient.pageArticles(cursor, size, userIds);
            if (result.getCode() == 200) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("获取文章分页失败: cursor={}, size={}", cursor, size, e);
        }
        return Collections.emptyList();
    }

    /** 经 Feign 批量获取作者信息；失败降级为空（FeedItem 作者字段为空） */
    private Map<Long, UserBrief> fetchUsers(List<Long> userIds) {
        try {
            Result<List<UserBrief>> result = authClient.batchUsers(userIds);
            if (result.getCode() == 200 && result.getData() != null) {
                return result.getData().stream().collect(Collectors.toMap(UserBrief::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("批量获取作者信息失败: userIds={}", userIds, e);
        }
        return Collections.emptyMap();
    }

    /**
     * 获取 FeedItem：优先从 L3 Redis 文章片段缓存获取，否则构建并写入
     */
    private FeedItem getFeedItemFromL3OrBuild(ArticleBrief article, Map<Long, UserBrief> userMap) {
        String l3Key = "article:detail:" + article.getId();

        // 热点探测
        checkAndPromoteHot(article.getId());

        // L3 Redis 文章片段缓存
        String l3Cached = redisTemplate.opsForValue().get(l3Key);
        if (l3Cached != null) {
            try {
                return objectMapper.readValue(l3Cached, FeedItem.class);
            } catch (JsonProcessingException e) {
                log.warn("L3缓存反序列化失败, articleId={}", article.getId(), e);
            }
        }

        // 构建 FeedItem
        UserBrief author = userMap.get(article.getUserId());
        FeedItem item = new FeedItem();
        item.setId(article.getId());
        item.setUserId(article.getUserId());
        item.setUsername(author != null ? author.getUsername() : "");
        item.setAvatarUrl(author != null ? author.getAvatarUrl() : null);
        item.setTitle(article.getTitle());
        item.setSummary(article.getSummary());
        item.setCoverUrl(article.getCoverUrl());
        item.setTags(article.getTags());
        item.setLikeCount(getRedisCount("article:likes:" + article.getId(), article.getLikeCount()));
        item.setCollectCount(getRedisCount("article:collects:" + article.getId(), article.getCollectCount()));
        item.setViewCount(article.getViewCount());
        item.setCreateTime(article.getCreateTime());

        // 写入 L3 缓存
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForValue().set(l3Key, json, 60, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("L3缓存序列化失败, articleId={}", article.getId(), e);
        }

        return item;
    }

    /**
     * 热点探测：10分钟内访问超100次自动延长 L3 缓存 TTL
     */
    private void checkAndPromoteHot(Long articleId) {
        String hotKey = "article:hot:" + articleId;
        Long count = redisTemplate.opsForValue().increment(hotKey);
        if (count != null && count == 1) {
            redisTemplate.expire(hotKey, 10, TimeUnit.MINUTES);
        }
        if (count != null && count > 100) {
            String l3Key = "article:detail:" + articleId;
            long extraTtl = 300 + new Random().nextInt(30);
            redisTemplate.expire(l3Key, extraTtl, TimeUnit.SECONDS);
        }
    }

    /**
     * 从 Redis 获取实时计数，若 Redis 无值则回退到 DB 值（来自 article-service 的 ArticleBrief）
     */
    private Integer getRedisCount(String key, Integer dbFallback) {
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                log.warn("Redis计数解析失败, key={}", key, e);
            }
        }
        return dbFallback;
    }
}
