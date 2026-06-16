package com.knowledgecommunity.modules.ai.agent.config;

import com.knowledgecommunity.modules.ai.agent.tools.*;
import com.knowledgecommunity.modules.article.service.ArticleService;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

/**
 * Agent 工具工厂：不注册任何 Bean，避免 Spring AI ToolCallbackResolver 自动扫描导致循环依赖
 * 由 AgentService 在构造时调用静态方法获取 ToolCallback 列表
 */
public class AgentConfig {

    /**
     * 创建 Agent 工具回调列表（非 Spring Bean，不参与自动扫描）
     */
    public static List<ToolCallback> createToolCallbacks(OpenAiChatModel chatModel,
                                                          ArticleService articleService) {
        MethodToolCallbackProvider toolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(
                        new OutlineTool(chatModel),
                        new ContinueTool(chatModel),
                        new PolishTool(chatModel),
                        new SummaryTool(chatModel),
                        new TagRecommendTool(chatModel),
                        new PublishTool(articleService)
                )
                .build();

        return List.of(toolProvider.getToolCallbacks());
    }
}
