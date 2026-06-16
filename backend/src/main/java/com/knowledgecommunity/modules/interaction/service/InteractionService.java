package com.knowledgecommunity.modules.interaction.service;

import com.knowledgecommunity.modules.interaction.dto.LikeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 互动服务：点赞/收藏
 * - 使用 Redis bitmap 记录用户是否点赞/收藏（userId 作为 bit offset）
 * - 使用 Redis String 记录计数
 * - 通过 Lua 脚本保证 bitmap 操作和计数操作的原子性
 * - 计数由 CountSyncJob 定时同步回 DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;

    /** 点赞 Lua: 检查位图 -> 若未点赞则 SETBIT 1 + INCR 计数 */
    private static final String LIKE_SCRIPT =
            "local bitmapKey = KEYS[1] " +
            "local counterKey = KEYS[2] " +
            "local offset = tonumber(ARGV[1]) " +
            "local current = redis.call('GETBIT', bitmapKey, offset) " +
            "if current == 1 then " +
            "  return {0, tonumber(redis.call('GET', counterKey) or '0')} " +
            "end " +
            "redis.call('SETBIT', bitmapKey, offset, 1) " +
            "local count = redis.call('INCR', counterKey) " +
            "return {1, count}";

    /** 取消点赞 Lua: 检查位图 -> 若已点赞则 SETBIT 0 + DECR 计数(不小于0) */
    private static final String UNLIKE_SCRIPT =
            "local bitmapKey = KEYS[1] " +
            "local counterKey = KEYS[2] " +
            "local offset = tonumber(ARGV[1]) " +
            "local current = redis.call('GETBIT', bitmapKey, offset) " +
            "if current == 0 then " +
            "  return {0, tonumber(redis.call('GET', counterKey) or '0')} " +
            "end " +
            "redis.call('SETBIT', bitmapKey, offset, 0) " +
            "local count = tonumber(redis.call('GET', counterKey) or '0') " +
            "if count > 0 then " +
            "  count = redis.call('DECR', counterKey) " +
            "end " +
            "return {1, count}";

    /** 收藏 Lua（逻辑同点赞） */
    private static final String COLLECT_SCRIPT =
            "local bitmapKey = KEYS[1] " +
            "local counterKey = KEYS[2] " +
            "local offset = tonumber(ARGV[1]) " +
            "local current = redis.call('GETBIT', bitmapKey, offset) " +
            "if current == 1 then " +
            "  return {0, tonumber(redis.call('GET', counterKey) or '0')} " +
            "end " +
            "redis.call('SETBIT', bitmapKey, offset, 1) " +
            "local count = redis.call('INCR', counterKey) " +
            "return {1, count}";

    /** 取消收藏 Lua（逻辑同取消点赞） */
    private static final String UNCOLLECT_SCRIPT =
            "local bitmapKey = KEYS[1] " +
            "local counterKey = KEYS[2] " +
            "local offset = tonumber(ARGV[1]) " +
            "local current = redis.call('GETBIT', bitmapKey, offset) " +
            "if current == 0 then " +
            "  return {0, tonumber(redis.call('GET', counterKey) or '0')} " +
            "end " +
            "redis.call('SETBIT', bitmapKey, offset, 0) " +
            "local count = tonumber(redis.call('GET', counterKey) or '0') " +
            "if count > 0 then " +
            "  count = redis.call('DECR', counterKey) " +
            "end " +
            "return {1, count}";

    /** 点赞文章 */
    public LikeResult like(Long articleId, Long userId) {
        LikeResult result = executeScript(LIKE_SCRIPT,
                "article:like:bitmap:" + articleId,
                "article:likes:" + articleId,
                userId);
        if (result.isLiked()) {
            invalidateFeedCache(articleId);
        }
        return result;
    }

    /** 取消点赞 */
    public LikeResult unlike(Long articleId, Long userId) {
        LikeResult result = executeScript(UNLIKE_SCRIPT,
                "article:like:bitmap:" + articleId,
                "article:likes:" + articleId,
                userId);
        if (result.isLiked()) {
            invalidateFeedCache(articleId);
        }
        return result;
    }

    /** 收藏文章 */
    public LikeResult collect(Long articleId, Long userId) {
        LikeResult result = executeScript(COLLECT_SCRIPT,
                "article:collect:bitmap:" + articleId,
                "article:collects:" + articleId,
                userId);
        if (result.isLiked()) {
            invalidateFeedCache(articleId);
        }
        return result;
    }

    /** 取消收藏 */
    public LikeResult uncollect(Long articleId, Long userId) {
        LikeResult result = executeScript(UNCOLLECT_SCRIPT,
                "article:collect:bitmap:" + articleId,
                "article:collects:" + articleId,
                userId);
        if (result.isLiked()) {
            invalidateFeedCache(articleId);
        }
        return result;
    }

    /** 清除文章相关的 Feed 缓存（L1 + L2 + L3） */
    private void invalidateFeedCache(Long articleId) {
        // 清除 L3 文章片段缓存
        redisTemplate.delete("article:detail:" + articleId);

        // 清除 L2 Redis Feed 页面缓存
        Set<String> feedKeys = redisTemplate.keys("feed:*");
        if (feedKeys != null && !feedKeys.isEmpty()) {
            redisTemplate.delete(feedKeys);
        }

        // 清除 L1 Caffeine Feed 缓存
        Cache feedCache = cacheManager.getCache("feed");
        if (feedCache != null) {
            feedCache.clear();
        }
    }

    /**
     * 执行 Lua 脚本
     * @param script     Lua 脚本
     * @param bitmapKey  bitmap 键（记录用户操作状态）
     * @param counterKey 计数器键
     * @param userId     用户ID（作为 bitmap offset）
     * @return 操作结果（是否操作成功 + 最新计数）
     */
    private LikeResult executeScript(String script, String bitmapKey, String counterKey, Long userId) {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(script, List.class);
        List<Long> result = redisTemplate.execute(redisScript,
                List.of(bitmapKey, counterKey),
                String.valueOf(userId));
        if (result != null && result.size() >= 2) {
            return new LikeResult(result.get(0) == 1, result.get(1).intValue());
        }
        return new LikeResult(false, 0);
    }
}
