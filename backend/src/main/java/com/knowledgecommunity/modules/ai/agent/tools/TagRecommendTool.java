package com.knowledgecommunity.modules.ai.agent.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：根据内容推荐文章标签
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagRecommendTool {

    private final ChatClient.Builder chatClientBuilder;

    @Tool(description = "根据内容推荐文章标签")
    public String recommendTags(@ToolParam(description = "文章内容") String content) {
        return chatClientBuilder.build()
                .prompt()
                .system("你是一个内容标签专家。请根据用户提供的文章内容，推荐3-5个合适的标签。只返回逗号分隔的标签字符串，不要加任何前缀、解释或编号。例如：Spring Boot,微服务,Java")
                .user("文章内容：\n" + content + "\n\n推荐标签：")
                .call()
                .content();
    }
}