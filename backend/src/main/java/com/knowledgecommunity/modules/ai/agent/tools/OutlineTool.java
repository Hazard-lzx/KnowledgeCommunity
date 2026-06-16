package com.knowledgecommunity.modules.ai.agent.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Agent 工具：根据标题生成文章大纲
 * 注意：不加 @Component，由 AgentConfig 手动注册，避免循环依赖
 */
public class OutlineTool {

    private final OpenAiChatModel chatModel;

    public OutlineTool(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "根据文章标题生成一份结构化大纲，返回Markdown列表格式，包含至少3个二级标题，每个二级标题下包含2-3个要点")
    public String generateOutline(@ToolParam(description = "文章标题") String title) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .system("你是一个结构化的写作助手。请根据用户提供的标题，生成一份详细的文章大纲，包含至少3个二级标题，每个二级标题下包含2-3个要点。使用 Markdown 列表格式输出。直接输出大纲，不要加任何前缀或解释。")
                .user("标题：" + title + "\n\n大纲：")
                .call()
                .content();
    }
}
