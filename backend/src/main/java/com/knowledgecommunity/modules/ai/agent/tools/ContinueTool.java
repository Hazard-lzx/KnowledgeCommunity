package com.knowledgecommunity.modules.ai.agent.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Agent 工具：根据上文续写内容
 */
public class ContinueTool {

    private final OpenAiChatModel chatModel;

    public ContinueTool(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "根据提供的上文内容，自然流畅地续写100-200字，保持风格一致")
    public String continueWrite(@ToolParam(description = "上文内容") String content) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .system("你是一个专业的内容创作者。请根据用户提供的上文，自然流畅地续写 100-200 字，保持风格一致。直接输出续写内容，不要加任何前缀或解释。")
                .user("上文：\n" + content + "\n\n请续写：")
                .call()
                .content();
    }
}
