package com.knowledgecommunity.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置
 * 用于 Feed 流 L1 缓存，最大500条，5秒写后过期
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public Caffeine<Object, Object> caffeine() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.SECONDS);
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
}
