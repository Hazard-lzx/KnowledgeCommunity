package com.knowledgecommunity.modules.ai.agent.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：根据内容生成文章摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryTool {

    private final ChatClient.Builder chatClientBuilder;

    @Tool(description = "根据内容生成文章摘要")
    public String generateSummary(@ToolParam(description = "文章内容") String content) {
        return chatClientBuilder.build()
                .prompt()
                .system("你是一个专业的内容编辑。请根据用户提供的文章内容，生成一份200字以内的摘要，提炼核心观点。直接输出摘要，不要加任何前缀或解释。")
                .user("文章内容：\n" + content + "\n\n摘要：")
                .call()
                .content();
    }
}