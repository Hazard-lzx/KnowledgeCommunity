package com.knowledgecommunity.modules.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置类
 *
 * 手动创建 EmbeddingModel Bean，因为 Spring AI 1.0.0 的
 * OpenAiEmbeddingAutoConfiguration 在 @ConditionalOnProperty 上
 * 没有设置 matchIfMissing=true，导致某些环境下不自动创建。
 * ChatClient.Builder 和 ChatModel 的自动配置正常，无需手动处理。
 */
@Configuration
public class AiConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-v2}") String model) {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                org.springframework.ai.document.MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(model)
                        .build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }
}