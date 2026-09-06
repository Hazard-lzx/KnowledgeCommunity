package com.knowledgecommunity.article.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgecommunity.article.article.entity.Article;
import com.knowledgecommunity.article.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 计数同步定时任务（article 表所有权在 article-service，随域迁移）
 * 每5秒将 Redis 中的点赞/收藏/浏览计数同步回 MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountSyncJob {

    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;

    /** 每5秒执行一次 */
    @Scheduled(fixedDelay = 5000)
    public void syncCounts() {
        syncCountType("article:likes:", "like_count");
        syncCountType("article:collects:", "collect_count");
        syncCountType("article:views:", "view_count");
    }

    private void syncCountType(String keyPrefix, String columnName) {
        Set<String> keys = redisTemplate.keys(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<Article> updates = new ArrayList<>();
        for (String key : keys) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) continue;

                String idStr = key.substring(keyPrefix.length());
                Long articleId = Long.parseLong(idStr);
                int count = Integer.parseInt(value);

                Article article = new Article();
                article.setId(articleId);
                switch (columnName) {
                    case "like_count" -> article.setLikeCount(count);
                    case "collect_count" -> article.setCollectCount(count);
                    case "view_count" -> article.setViewCount(count);
                }
                updates.add(article);
            } catch (NumberFormatException e) {
                log.warn("解析计数键失败: key={}", key);
            }
        }

        for (Article article : updates) {
            try {
                articleMapper.updateById(article);
            } catch (Exception e) {
                log.error("同步计数到MySQL失败: articleId={}", article.getId(), e);
            }
        }

        if (!updates.isEmpty()) {
            log.debug("同步{}计数完成, 共{}条", columnName, updates.size());
        }
    }
}
