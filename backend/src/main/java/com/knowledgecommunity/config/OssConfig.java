package com.knowledgecommunity.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置属性
 * 读取 application.yml 中 oss.* 前缀的配置
 *
 * 注意：不标 @Configuration，而是通过 @EnableConfigurationProperties 注册。
 * 这是 Spring Boot 官方推荐的方式，避免 @Configuration 代理导致属性绑定失败。
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "oss")
public class OssConfig {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String urlPrefix;

    @PostConstruct
    public void validate() {
        log.info("OSS 配置加载: endpoint={}, bucket={}, accessKeyId={}",
                endpoint, bucketName,
                accessKeyId != null ? accessKeyId.substring(0, Math.min(8, accessKeyId.length())) + "***" : "null");
        if (endpoint == null || bucketName == null || accessKeyId == null || accessKeySecret == null) {
            log.error("OSS 配置不完整！请检查 application.yml 或 application-dev.yml 中的 oss.* 配置");
        }
    }
}
