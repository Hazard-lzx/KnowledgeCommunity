package com.knowledgecommunity.config;

import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 配置
 * 使用 Spring Boot 自动配置的 ElasticsearchTemplate
 * 不再通过 @EnableElasticsearchRepositories 强制创建 Repository Bean
 * 避免在 ES 不可用时导致应用启动失败
 */
@Configuration
public class ElasticsearchConfig {
}
