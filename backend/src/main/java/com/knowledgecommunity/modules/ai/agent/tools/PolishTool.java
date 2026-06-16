package com.knowledgecommunity.modules.ai.agent.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Agent 工具：润色文本
 */
public class PolishTool {

    private final OpenAiChatModel chatModel;

    public PolishTool(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "优化文本的表达，修正语病，使其更流畅、专业，但保持原意不变")
    public String polishText(@ToolParam(description = "需要润色的原文") String content) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .system("你是一个专业的文字编辑。请优化以下文本的表达，修正语病，使其更流畅、专业，但保持原意不变。直接输出优化后的文本，不要加任何前缀或解释。")
                .user("原文：\n" + content + "\n\n优化后：")
                .call()
                .content();
    }
}
