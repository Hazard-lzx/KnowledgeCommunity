package com.knowledgecommunity.article.job;

import com.knowledgecommunity.article.article.entity.Article;
import com.knowledgecommunity.article.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 计数校验定时任务（随 article 域迁移）
 * 每小时对比 Redis 和 MySQL 中的计数，不一致则以 Redis 为准修复 MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountCheckJob {

    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;

    /** 每小时执行一次 */
    @Scheduled(fixedDelay = 3600000)
    public void checkCounts() {
        log.info("开始计数校验任务");

        checkCountType("article:likes:", "like_count");
        checkCountType("article:collects:", "collect_count");
        checkCountType("article:views:", "view_count");

        log.info("计数校验任务完成");
    }

    private void checkCountType(String keyPrefix, String columnName) {
        Set<String> keys = redisTemplate.keys(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int inconsistent = 0;
        for (String key : keys) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) continue;

                String idStr = key.substring(keyPrefix.length());
                Long articleId = Long.parseLong(idStr);
                int redisCount = Integer.parseInt(value);

                Article article = articleMapper.selectById(articleId);
                if (article == null) continue;

                int mysqlCount = switch (columnName) {
                    case "like_count" -> article.getLikeCount();
                    case "collect_count" -> article.getCollectCount();
                    case "view_count" -> article.getViewCount();
                    default -> 0;
                };

                if (redisCount != mysqlCount) {
                    Article update = new Article();
                    update.setId(articleId);
                    switch (columnName) {
                        case "like_count" -> update.setLikeCount(redisCount);
                        case "collect_count" -> update.setCollectCount(redisCount);
                        case "view_count" -> update.setViewCount(redisCount);
                    }
                    articleMapper.updateById(update);
                    inconsistent++;
                    log.warn("计数不一致已修复: articleId={}, {}, redis={}, mysql={}",
                            articleId, columnName, redisCount, mysqlCount);
                }
            } catch (Exception e) {
                log.error("计数校验异常: key={}", key, e);
            }
        }

        if (inconsistent > 0) {
            log.info("{} 计数校验发现 {} 条不一致已修复", columnName, inconsistent);
        }
    }
}
