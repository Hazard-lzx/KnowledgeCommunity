package com.knowledgecommunity.modules.ai.agent.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：根据上文续写文章内容
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContinueTool {

    private final ChatClient.Builder chatClientBuilder;

    @Tool(description = "根据上文续写文章内容")
    public String continueWrite(@ToolParam(description = "上文内容") String context) {
        return chatClientBuilder.build()
                .prompt()
                .system("你是一个专业的内容创作者。请根据用户提供的上文，自然流畅地续写 100-200 字，保持风格一致。直接输出续写内容，不要加任何前缀或解释。")
                .user("上文：\n" + context + "\n\n请续写：")
                .call()
                .content();
    }
}