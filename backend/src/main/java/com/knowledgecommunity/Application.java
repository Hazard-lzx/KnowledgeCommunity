package com.knowledgecommunity;

import com.knowledgecommunity.config.OssConfig;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {OpenAiEmbeddingAutoConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties(OssConfig.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("============================================");
        System.out.println("  Knowledge Community后端服务启动成功！");
        System.out.println("  API文档地址：http://localhost:8080/doc.html");
        System.out.println("============================================");

    }
}
