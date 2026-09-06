package com.knowledgecommunity.article;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文章服务：article + interaction
 * 只信网关身份头（X-User-Id / X-Username），不解析入站 JWT
 */
@SpringBootApplication(scanBasePackages = "com.knowledgecommunity")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.knowledgecommunity.article.client")
@EnableScheduling
@MapperScan("com.knowledgecommunity.article")
public class ArticleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArticleApplication.class, args);
    }
}
