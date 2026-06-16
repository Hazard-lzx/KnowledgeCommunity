package com.knowledgecommunity.modules.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgecommunity.modules.article.entity.Article;
import org.apache.ibatis.annotations.Mapper;

/** 文章 Mapper，基于 MyBatis-Plus BaseMapper */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
}
