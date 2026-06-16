package com.knowledgecommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置属性
 * 读取 application.yml 中 oss.* 前缀的配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "oss")
public class OssConfig {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String urlPrefix;
}
