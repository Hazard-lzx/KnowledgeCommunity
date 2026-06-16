package com.knowledgecommunity.modules.ai.agent.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Agent 工具：根据文章内容推荐标签
 */
public class TagRecommendTool {

    private final OpenAiChatModel chatModel;

    public TagRecommendTool(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "根据文章内容推荐3-5个标签，返回逗号分隔的标签字符串")
    public String recommendTags(@ToolParam(description = "文章内容或标题") String content) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .system("你是一个内容标签专家。请根据用户提供的文章内容，推荐3-5个合适的标签。只返回逗号分隔的标签字符串，不要加任何前缀、解释或编号。例如：Spring Boot,微服务,Java")
                .user("文章内容：\n" + content + "\n\n推荐标签：")
                .call()
                .content();
    }
}
