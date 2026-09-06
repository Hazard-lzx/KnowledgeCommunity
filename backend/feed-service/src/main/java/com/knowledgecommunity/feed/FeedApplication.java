package com.knowledgecommunity.feed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Feed 服务：三级缓存瀑布流（不直连 MySQL，数据经 Feign 拉取）
 * 只信网关身份头（X-User-Id / X-Username），不解析入站 JWT
 */
@SpringBootApplication(scanBasePackages = "com.knowledgecommunity")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.knowledgecommunity.feed.client")
@EnableCaching
public class FeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedApplication.class, args);
    }
}
