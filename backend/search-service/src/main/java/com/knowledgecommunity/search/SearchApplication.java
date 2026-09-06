package com.knowledgecommunity.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 搜索服务：ES 文章搜索与联想建议（只读，不直连 MySQL）
 * 只信网关身份头（X-User-Id / X-Username），不解析入站 JWT
 */
@SpringBootApplication(scanBasePackages = "com.knowledgecommunity")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.knowledgecommunity.search.client")
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
