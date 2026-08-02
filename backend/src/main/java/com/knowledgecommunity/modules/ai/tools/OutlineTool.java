package com.knowledgecommunity.modules.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：根据标题生成文章结构化大纲
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutlineTool {

    private final ChatClient.Builder chatClientBuilder;

    @Tool(description = "根据标题生成文章结构化大纲")
    public String generateOutline(@ToolParam(description = "文章标题") String title) {
        return chatClientBuilder.build()
                .prompt()
                .system("你是一个结构化的写作助手。请根据用户提供的标题，生成一份详细的文章大纲，包含至少3个二级标题，每个二级标题下包含2-3个要点。使用 Markdown 列表格式输出。直接输出大纲，不要加任何前缀或解释。")
                .user("标题：" + title + "\n\n大纲：")
                .call()
                .content();
    }
}