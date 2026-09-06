"""Agent 会话记忆：Redis list，2 小时 TTL，超 20 条自动压缩（保留最近 10 条 + AI 摘要）

存储格式与单体 AgentMemoryService 对齐：JSON {"type": "...", "content": "..."}，
key = agent:session:{sessionId}，兼容读取单体写入的大写类型（USER/ASSISTANT/SYSTEM/TOOL）。
"""

import json

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app import config
from app.agent.utils import content_to_text
from app.core.llm import get_tool_llm
from app.core.redis import get_redis

_TYPE_MAP = {
    SystemMessage: "system",
    HumanMessage: "user",
    AIMessage: "ai",
}


def _key(session_id: str) -> str:
    return f"agent:session:{session_id}"


def _serialize(msg) -> dict:
    msg_type = _TYPE_MAP.get(type(msg))
    if msg_type is None:  # ToolMessage 等
        msg_type = "tool"
    return {"type": msg_type, "content": content_to_text(msg.content)}


def _deserialize(item: dict):
    t = item.get("type", "").upper()
    content = item.get("content", "")
    if t == "SYSTEM":
        return SystemMessage(content=content)
    if t == "ASSISTANT" or t == "AI":
        return AIMessage(content=content)
    if t == "TOOL":  # 恢复会话时孤儿 tool 消息无意义，降级为 user
        return HumanMessage(content=content)
    return HumanMessage(content=content)


async def append_messages(session_id: str, msgs: list) -> None:
    r = get_redis()
    key = _key(session_id)
    for m in msgs:
        await r.rpush(key, json.dumps(_serialize(m), ensure_ascii=False))
    await r.expire(key, config.SESSION_TTL_HOURS * 3600)
    size = await r.llen(key)
    if size > config.SESSION_MAX_MESSAGES:
        await compact(session_id, size)


async def compact(session_id: str, size: int) -> None:
    """超阈值压缩：删旧留新（最近 KEEP_RECENT 条），头部插入 AI 生成的摘要"""
    r = get_redis()
    key = _key(session_id)
    to_remove = size - config.SESSION_KEEP_RECENT
    old = await r.lrange(key, 0, to_remove - 1)
    for _ in range(to_remove):
        await r.lpop(key)
    summary = await _summarize(old)
    await r.lpush(key, json.dumps({"type": "system", "content": summary}, ensure_ascii=False))
    await r.expire(key, config.SESSION_TTL_HOURS * 3600)


async def _summarize(old_items: list[str]) -> str:
    text = "\n".join(f"[{json.loads(i).get('type')}] {json.loads(i).get('content', '')[:300]}" for i in old_items)
    try:
        resp = await get_tool_llm().ainvoke(
            HumanMessage(content=f"请将以下 Agent 对话历史压缩为 300 字以内的摘要，保留关键创作决策与已完成步骤：\n{text}")
        )
        return content_to_text(resp.content)
    except Exception:
        return "（历史摘要生成失败，截断保留）" + text[:800]


async def load_history(session_id: str) -> list:
    """恢复会话：过滤掉带 tool_calls 的 AI 消息，保证重建的对话可安全送入模型"""
    r = get_redis()
    raw = await r.lrange(_key(session_id), 0, -1)
    messages = []
    for item in raw:
        try:
            messages.append(_deserialize(json.loads(item)))
        except (ValueError, TypeError):
            continue
    return messages


async def clear_session(session_id: str) -> None:
    await get_redis().delete(_key(session_id))
