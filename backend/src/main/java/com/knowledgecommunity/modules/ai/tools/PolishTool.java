package com.knowledgecommunity.modules.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：润色文本，优化表达方式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolishTool {

    private final ChatClient.Builder chatClientBuilder;

    @Tool(description = "润色文本，优化表达方式")
    public String polishText(@ToolParam(description = "待润色的文本") String text) {
        return chatClientBuilder.build()
                .prompt()
                .system("你是一个专业的文字编辑。请优化以下文本的表达，修正语病，使其更流畅、专业，但保持原意不变。直接输出优化后的文本，不要加任何前缀或解释。")
                .user("原文：\n" + text + "\n\n优化后：")
                .call()
                .content();
    }
}