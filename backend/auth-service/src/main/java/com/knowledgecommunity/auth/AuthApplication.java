package com.knowledgecommunity.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户认证服务：user + auth + OSS
 * 不解析入站 JWT（网关已验签注入身份头），仅登录时签发 JWT
 */
@SpringBootApplication(scanBasePackages = "com.knowledgecommunity")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.knowledgecommunity.auth.client")
@EnableScheduling
@MapperScan("com.knowledgecommunity.auth")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
