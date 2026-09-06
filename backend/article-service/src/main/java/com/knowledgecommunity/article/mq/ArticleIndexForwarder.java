package com.knowledgecommunity.article.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 索引事件转发器（随 article 域迁移）：消费 ARTICLE_INDEX_EVENT（article 发布/更新/删除时发出），
 * HTTP 转发给 ai-service 的 /internal/index 接口做 Milvus 向量索引。
 *
 * 说明：RocketMQ 4.x remoting 协议无官方 Python 客户端，故由 Java 侧消费后转发；
 * ai-service 不可用时抛出异常触发 RocketMQ 重投，实现异步解耦。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "ARTICLE_INDEX_EVENT",
        consumerGroup = "article-index-forwarder"
)
public class ArticleIndexForwarder implements RocketMQListener<String> {

    private final HttpClient httpClient;
    private final String pythonBaseUrl;
    private final String internalToken;

    public ArticleIndexForwarder(
            @Value("${internal.python-base-url}") String pythonBaseUrl,
            @Value("${internal.token}") String internalToken) {
        this.pythonBaseUrl = pythonBaseUrl;
        this.internalToken = internalToken;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void onMessage(String message) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonBaseUrl + "/internal/index"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("X-Trace-Id", java.util.UUID.randomUUID().toString().replace("-", ""))
                    .POST(HttpRequest.BodyPublishers.ofString(message))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("索引事件已转发至 ai-service: {}", message);
            } else {
                throw new IllegalStateException("ai-service 返回 " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            log.error("转发索引事件失败（将触发MQ重投）: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}
