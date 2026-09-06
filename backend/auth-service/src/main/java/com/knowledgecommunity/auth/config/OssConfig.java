package com.knowledgecommunity.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置属性
 */
@Slf4j
@Data
@Configuration
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
