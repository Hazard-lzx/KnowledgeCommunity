package com.knowledgecommunity.modules.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.modules.article.entity.Article;
import com.knowledgecommunity.modules.article.mapper.ArticleMapper;
import com.knowledgecommunity.modules.auth.entity.User;
import com.knowledgecommunity.modules.auth.mapper.UserMapper;
import com.knowledgecommunity.modules.feed.dto.FeedItem;
import com.knowledgecommunity.modules.feed.dto.FeedResponse;
import com.knowledgecommunity.modules.user.entity.UserFollow;
import com.knowledgecommunity.modules.user.mapper.UserFollowMapper;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
 * 单飞锁：同一页面的并发请求只有一个线程查询数据库，其余等待结果
 * 热点探测：10分钟内访问超100次自动延长 L3 缓存 TTL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final UserFollowMapper userFollowMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    /** 单飞锁映射，防止缓存击穿时大量并发查询 DB */
    private final ConcurrentHashMap<String, ReentrantLock> flightLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取 Feed 流
     * 缓存查询顺序：L1 Caffeine → L2 Redis 页面 → 单飞锁 + DB
     * 未登录用户：展示全站最新文章
     * 已登录用户：展示关注用户的文章
     */
    public FeedResponse getFeed(UserPrincipal currentUser, String cursor, int size) {
        Long userId = currentUser != null ? currentUser.getUserId() : 0L;
        String cacheKey = "feed:" + userId + ":" + (cursor != null ? cursor : "latest");

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

        // 单飞锁：同一页面缓存未命中时只有一个线程查询数据库
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

            // 查询数据库
            FeedResponse response = queryFeedFromDB(currentUser, cursor, size);

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
     * 从数据库查询 Feed 数据
     * 未登录用户：查询全站最新文章
     * 已登录用户：查询关注用户的已发布文章（游标分页）
     */
    private FeedResponse queryFeedFromDB(UserPrincipal currentUser, String cursor, int size) {
        List<Long> followeeIds = null;

        if (currentUser != null) {
            // 已登录：获取关注列表
            List<UserFollow> follows = userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUser.getUserId())
                            .eq(UserFollow::getStatus, 1)
                            .select(UserFollow::getFolloweeId)
            );

            followeeIds = follows.stream()
                    .map(UserFollow::getFolloweeId)
                    .collect(Collectors.toList());

            // 关注列表为空时，展示全站最新文章（包含自己的）
            if (followeeIds.isEmpty()) {
                followeeIds = null;
            } else {
                // 确保自己的文章也包含在 Feed 中
                followeeIds.add(currentUser.getUserId());
            }
        }

        // 查询文章（游标分页，多取一条判断是否有下一页）
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1);

        if (followeeIds != null) {
            query.in(Article::getUserId, followeeIds);
        }

        if (StringUtils.isNotBlank(cursor)) {
            LocalDateTime cursorTime = LocalDateTime.parse(cursor, FORMATTER);
            query.lt(Article::getCreateTime, cursorTime);
        }

        query.orderByDesc(Article::getCreateTime).last("LIMIT " + (size + 1));

        List<Article> articles = articleMapper.selectList(query);

        boolean hasMore = articles.size() > size;
        if (hasMore) {
            articles = articles.subList(0, size);
        }

        // 批量获取作者信息
        Map<Long, User> userMap = new HashMap<>();
        if (!articles.isEmpty()) {
            List<Long> userIds = articles.stream().map(Article::getUserId).distinct().collect(Collectors.toList());
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
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

    /**
     * 获取 FeedItem：优先从 L3 Redis 文章片段缓存获取，否则构建并写入
     */
    private FeedItem getFeedItemFromL3OrBuild(Article article, Map<Long, User> userMap) {
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
        User author = userMap.get(article.getUserId());
        FeedItem item = new FeedItem();
        item.setId(article.getId());
        item.setUserId(article.getUserId());
        item.setUsername(author != null ? author.getUsername() : "");
        item.setAvatarUrl(author != null ? author.getAvatarUrl() : null);
        item.setTitle(article.getTitle());
        item.setSummary(article.getSummary());
        item.setCoverUrl(article.getCoverUrl());
        item.setTags(StringUtils.isNotBlank(article.getTags()) ? article.getTags().split(",") : new String[0]);
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
     * 使用 Redis 计数器统计访问频率
     */
    private void checkAndPromoteHot(Long articleId) {
        String hotKey = "article:hot:" + articleId;
        Long count = redisTemplate.opsForValue().increment(hotKey);
        if (count != null && count == 1) {
            redisTemplate.expire(hotKey, 10, TimeUnit.MINUTES);
        }
        // 超过100次访问，延长文章片段缓存 TTL 到 300~330 秒
        if (count != null && count > 100) {
            String l3Key = "article:detail:" + articleId;
            long extraTtl = 300 + new Random().nextInt(30);
            redisTemplate.expire(l3Key, extraTtl, TimeUnit.SECONDS);
        }
    }

    /**
     * 从 Redis 获取实时计数，若 Redis 无值则回退到 DB 值
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
