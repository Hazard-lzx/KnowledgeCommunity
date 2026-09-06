package com.knowledgecommunity.article.article.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgecommunity.common.Result;
import com.knowledgecommunity.common.dto.FeedItem;
import com.knowledgecommunity.common.dto.UserBrief;
import com.knowledgecommunity.common.BusinessException;
import com.knowledgecommunity.article.article.dto.ArticleCreateRequest;
import com.knowledgecommunity.article.article.dto.ArticleDetailResponse;
import com.knowledgecommunity.article.article.entity.Article;
import com.knowledgecommunity.article.article.entity.ArticleDocument;
import com.knowledgecommunity.article.article.mapper.ArticleMapper;
import com.knowledgecommunity.article.client.AuthClient;
import com.knowledgecommunity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章服务
 * - 创建文章后同步到 ES，并发送 MQ 消息触发 AI 摘要（由 ai-service 生成）
 * - 文章详情的计数优先从 Redis 获取，点赞/收藏状态通过 bitmap 判断
 * - 作者信息/关注状态经 Feign 调 auth-service 内部接口获取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final AuthClient authClient;

    /**
     * 创建文章
     * 1. 入库 2. 同步 ES 3. 发送 MQ 触发 AI 摘要 4. 发送索引事件（Milvus）
     */
    public Long createArticle(UserPrincipal currentUser, ArticleCreateRequest request) {
        Article article = new Article();
        article.setUserId(currentUser.getUserId());
        article.setTitle(request.getTitle());
        article.setContent(request.getContent());
        article.setCoverUrl(request.getCoverUrl());
        article.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        article.setTags(request.getTags() != null ? String.join(",", request.getTags()) : null);
        article.setLikeCount(0);
        article.setCollectCount(0);
        article.setViewCount(0);
        articleMapper.insert(article);

        // 同步到 Elasticsearch
        syncToElasticsearch(article);

        // 已发布状态发送 MQ 消息，触发 AI 摘要生成
        if (article.getStatus() == 1) {
            rocketMQTemplate.syncSend("ARTICLE_PUBLISHED",
                    MessageBuilder.withPayload(article.getId()).build());
        }

        // 发送索引事件（ai-service 消费后写入 Milvus）
        sendArticleIndexEvent(article.getId(),
                article.getStatus() == 1 ? "PUBLISH" : "UPDATE");

        return article.getId();
    }

    /**
     * 获取文章详情
     * - 计数优先从 Redis 读取（定时任务同步 DB → Redis）
     * - 点赞/收藏状态通过 Redis bitmap 判断
     * - 每次访问增加浏览量
     */
    public ArticleDetailResponse getArticleDetail(Long id, UserPrincipal currentUser) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        UserBrief author = fetchUserBrief(article.getUserId());

        ArticleDetailResponse response = new ArticleDetailResponse();
        response.setId(article.getId());
        response.setUserId(article.getUserId());
        response.setUsername(author != null ? author.getUsername() : "");
        response.setAvatarUrl(author != null ? author.getAvatarUrl() : null);
        response.setTitle(article.getTitle());
        response.setContent(article.getContent());
        response.setSummary(article.getSummary());
        response.setCoverUrl(article.getCoverUrl());
        response.setStatus(article.getStatus());
        response.setTags(StringUtils.isNotBlank(article.getTags())
                ? article.getTags().split(",") : new String[0]);

        // 计数优先从 Redis 获取，Redis 无数据则回退到 DB
        response.setLikeCount(getCountFromRedis("article:likes:" + id, article.getLikeCount()));
        response.setCollectCount(getCountFromRedis("article:collects:" + id, article.getCollectCount()));
        response.setViewCount(getCountFromRedis("article:views:" + id, article.getViewCount()));

        response.setCreateTime(article.getCreateTime());
        response.setUpdateTime(article.getUpdateTime());

        // 检查当前用户是否已点赞/收藏（bitmap 方式）
        if (currentUser != null) {
            response.setLiked(isBitSet("article:like:bitmap:" + id, currentUser.getUserId()));
            response.setCollected(isBitSet("article:collect:bitmap:" + id, currentUser.getUserId()));

            // 检查当前用户是否已关注作者（auth-service 内部接口；失败降级为未关注）
            response.setFollowed(fetchFollowed(currentUser.getUserId(), article.getUserId()));
        } else {
            response.setLiked(false);
            response.setCollected(false);
            response.setFollowed(false);
        }

        // 增加浏览量（Redis 计数，定时任务同步回 DB）
        redisTemplate.opsForValue().increment("article:views:" + id);

        return response;
    }

    /**
     * 更新文章
     * 仅作者本人可编辑，更新后同步到 ES
     */
    public void updateArticle(UserPrincipal currentUser, Long id, ArticleCreateRequest request) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!article.getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException(403, "无权编辑此文章");
        }
        article.setTitle(request.getTitle());
        article.setContent(request.getContent());
        article.setCoverUrl(request.getCoverUrl());
        article.setTags(request.getTags() != null ? String.join(",", request.getTags()) : null);
        if (request.getStatus() != null) {
            article.setStatus(request.getStatus());
        }
        articleMapper.updateById(article);

        // 同步到 ES
        syncToElasticsearch(article);

        // 发送索引事件（ai-service 消费后更新 Milvus）
        sendArticleIndexEvent(article.getId(), "UPDATE");
    }

    /**
     * 删除文章（逻辑删除）
     * 仅作者本人可删除，删除后从 ES 移除
     */
    public void deleteArticle(UserPrincipal currentUser, Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!article.getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException(403, "无权删除此文章");
        }
        articleMapper.deleteById(id);

        // 从 ES 中移除
        try {
            elasticsearchTemplate.delete(id.toString(), ArticleDocument.class);
        } catch (Exception e) {
            log.error("从ES删除文章失败, articleId={}", id, e);
        }

        // 发送索引事件（ai-service 消费后从 Milvus 删除）
        sendArticleIndexEvent(id, "DELETE");
    }

    /**
     * 发送文章索引事件到 ai-service（经 RocketMQ 异步解耦）。
     * 发送失败仅记录日志，不影响业务主流程；索引缺失可通过回填脚本补偿。
     */
    private void sendArticleIndexEvent(Long articleId, String action) {
        try {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("articleId", articleId, "action", action));
            rocketMQTemplate.syncSend("ARTICLE_INDEX_EVENT",
                    MessageBuilder.withPayload(payload).build());
        } catch (Exception e) {
            log.error("发送文章索引事件失败, articleId={}, action={}", articleId, action, e);
        }
    }

    /** 同步文章到 Elasticsearch */
    private void syncToElasticsearch(Article article) {
        try {
            ArticleDocument doc = new ArticleDocument();
            doc.setId(article.getId());
            doc.setUserId(article.getUserId());
            doc.setTitle(article.getTitle());
            doc.setContent(article.getContent());
            doc.setSummary(article.getSummary());
            doc.setCoverUrl(article.getCoverUrl());
            doc.setStatus(article.getStatus());
            doc.setTags(StringUtils.isNotBlank(article.getTags())
                    ? article.getTags().split(",") : new String[0]);
            doc.setLikeCount(article.getLikeCount());
            doc.setCollectCount(article.getCollectCount());
            doc.setViewCount(article.getViewCount());
            doc.setCreateTime(article.getCreateTime());
            elasticsearchTemplate.save(doc);
        } catch (Exception e) {
            log.error("同步文章到ES失败, articleId={}", article.getId(), e);
        }
    }

    /** 从 Redis 获取计数，失败则回退到 DB 值 */
    private Integer getCountFromRedis(String key, Integer fallback) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            log.warn("从Redis获取计数失败, key={}", key);
        }
        return fallback;
    }

    /** 检查 bitmap 中某用户位是否已设置（用于判断点赞/收藏状态） */
    private Boolean isBitSet(String key, Long userId) {
        try {
            return redisTemplate.opsForValue().getBit(key, userId);
        } catch (Exception e) {
            return false;
        }
    }

    /** 经 Feign 获取用户简要信息；失败返回 null（调用方按匿名处理） */
    private UserBrief fetchUserBrief(Long userId) {
        try {
            Result<List<UserBrief>> result = authClient.batchUsers(List.of(userId));
            if (result.getCode() == 200 && result.getData() != null && !result.getData().isEmpty()) {
                return result.getData().get(0);
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}", userId, e);
        }
        return null;
    }

    /** 经 Feign 查询是否已关注；失败降级为 false */
    private boolean fetchFollowed(Long followerId, Long followeeId) {
        try {
            Result<Boolean> result = authClient.isFollowing(followerId, followeeId);
            return result.getCode() == 200 && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            log.warn("查询关注状态失败: followerId={}, followeeId={}", followerId, followeeId, e);
            return false;
        }
    }

    /** 获取指定用户的文章列表，返回 FeedItem（含作者信息和计数） */
    public List<FeedItem> getUserArticles(Long userId, Integer status, UserPrincipal currentUser) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .eq(status != null, Article::getStatus, status)
                .orderByDesc(Article::getCreateTime);
        List<Article> articles = articleMapper.selectList(wrapper);

        UserBrief author = fetchUserBrief(userId);
        Map<Long, UserBrief> authorMap = author != null
                ? Map.of(userId, author) : Collections.emptyMap();

        return articles.stream().map(article -> {
            FeedItem item = toFeedItem(article, authorMap);
            if (currentUser != null) {
                item.setLiked(isBitSet("article:like:bitmap:" + article.getId(), currentUser.getUserId()));
                item.setCollected(isBitSet("article:collect:bitmap:" + article.getId(), currentUser.getUserId()));
            } else {
                item.setLiked(false);
                item.setCollected(false);
            }
            return item;
        }).collect(Collectors.toList());
    }

    private FeedItem toFeedItem(Article article, Map<Long, UserBrief> authorMap) {
        FeedItem item = new FeedItem();
        item.setId(article.getId());
        item.setUserId(article.getUserId());
        UserBrief author = authorMap.get(article.getUserId());
        item.setUsername(author != null ? author.getUsername() : "");
        item.setAvatarUrl(author != null ? author.getAvatarUrl() : null);
        item.setTitle(article.getTitle());
        item.setSummary(article.getSummary());
        item.setCoverUrl(article.getCoverUrl());
        item.setTags(StringUtils.isNotBlank(article.getTags()) ? article.getTags().split(",") : new String[0]);
        item.setLikeCount(getCountFromRedis("article:likes:" + article.getId(), article.getLikeCount()));
        item.setCollectCount(getCountFromRedis("article:collects:" + article.getId(), article.getCollectCount()));
        item.setViewCount(getCountFromRedis("article:views:" + article.getId(), article.getViewCount()));
        item.setCreateTime(article.getCreateTime());
        return item;
    }

    /** Article → ArticleBrief（内部接口返回给 feed/search 的简要结构） */
    public static com.knowledgecommunity.common.dto.ArticleBrief toBrief(Article article) {
        com.knowledgecommunity.common.dto.ArticleBrief brief = new com.knowledgecommunity.common.dto.ArticleBrief();
        brief.setId(article.getId());
        brief.setUserId(article.getUserId());
        brief.setTitle(article.getTitle());
        brief.setSummary(article.getSummary());
        brief.setCoverUrl(article.getCoverUrl());
        brief.setTags(StringUtils.isNotBlank(article.getTags()) ? article.getTags().split(",") : new String[0]);
        brief.setLikeCount(article.getLikeCount());
        brief.setCollectCount(article.getCollectCount());
        brief.setViewCount(article.getViewCount());
        brief.setCreateTime(article.getCreateTime());
        return brief;
    }
}
