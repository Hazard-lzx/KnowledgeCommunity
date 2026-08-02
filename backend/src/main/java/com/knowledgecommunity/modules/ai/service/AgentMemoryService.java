package com.knowledgecommunity.modules.ai.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent 会话上下文管理
 * - 消息历史存储在 Redis，key = agent:session:{sessionId}
 * - 超过 20 条消息时自动摘要压缩：保留最近 5 轮，其余用 AI 生成摘要替换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long SESSION_TTL_HOURS = 2;
    private static final int MAX_MESSAGES = 20;
    private static final int KEEP_RECENT = 10; // 保留最近5轮(10条消息)

    /** 追加消息到会话历史 */
    public void appendMessage(String sessionId, Message message) {
        String key = "agent:session:" + sessionId;
        try {
            SerializedMessage sm = new SerializedMessage(
                    message.getMessageType().name(), message.getText());
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(sm));
            redisTemplate.expire(key, SESSION_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("序列化消息失败", e);
        }
    }

    /** 获取会话历史消息列表 */
    public List<Message> getMessages(String sessionId) {
        String key = "agent:session:" + sessionId;
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> messages = new ArrayList<>();
        for (String json : raw) {
            try {
                SerializedMessage sm = objectMapper.readValue(json, SerializedMessage.class);
                messages.add(deserializeMessage(sm));
            } catch (JsonProcessingException e) {
                log.warn("反序列化消息失败: {}", json, e);
            }
        }
        return messages;
    }

    /** 清除会话历史 */
    public void clearSession(String sessionId) {
        redisTemplate.delete("agent:session:" + sessionId);
    }

    /** 检查并压缩历史消息（超过阈值时保留最近消息，其余替换为摘要） */
    public void compactIfNeeded(String sessionId, String summary) {
        String key = "agent:session:" + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_MESSAGES) {
            // 删除旧消息，保留最近的
            long toRemove = size - KEEP_RECENT;
            for (long i = 0; i < toRemove; i++) {
                redisTemplate.opsForList().leftPop(key);
            }
            // 在头部插入摘要作为上下文
            SerializedMessage summaryMsg = new SerializedMessage("SYSTEM", summary);
            try {
                redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(summaryMsg));
            } catch (JsonProcessingException e) {
                log.error("序列化摘要消息失败", e);
            }
            log.info("会话 {} 历史压缩完成，摘要替换了 {} 条旧消息", sessionId, toRemove);
        }
    }

    private Message deserializeMessage(SerializedMessage sm) {
        return switch (sm.type) {
            case "USER" -> new UserMessage(sm.content);
            case "ASSISTANT" -> new AssistantMessage(sm.content);
            case "SYSTEM" -> new SystemMessage(sm.content);
            default -> new UserMessage(sm.content);
        };
    }

    /** 序列化消息 DTO */
    private record SerializedMessage(String type, String content) {}
}
