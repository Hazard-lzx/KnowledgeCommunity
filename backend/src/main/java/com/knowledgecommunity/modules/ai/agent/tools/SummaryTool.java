package com.knowledgecommunity.modules.ai.agent.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Agent 工具：生成文章摘要
 */
public class SummaryTool {

    private final OpenAiChatModel chatModel;

    public SummaryTool(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "根据文章全文生成200字以内的摘要，提炼核心观点")
    public String generateSummary(@ToolParam(description = "文章全文内容") String content) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .system("你是一个专业的内容编辑。请根据用户提供的文章内容，生成一份200字以内的摘要，提炼核心观点。直接输出摘要，不要加任何前缀或解释。")
                .user("文章内容：\n" + content + "\n\n摘要：")
                .call()
                .content();
    }
}
