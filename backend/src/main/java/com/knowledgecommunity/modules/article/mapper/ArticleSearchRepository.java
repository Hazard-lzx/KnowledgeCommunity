package com.knowledgecommunity.modules.article.mapper;

import com.knowledgecommunity.modules.article.entity.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** 文章 Elasticsearch 仓库，提供基础 CRUD 和搜索操作 */
public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, Long> {
}
