"""LangGraph ReAct Agent：15 轮上限 + Redis 会话 + SSE 事件流（thinking/tool_start/tool_result/final_chunk/done）"""

from typing import AsyncIterator

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.errors import GraphRecursionError
from langgraph.prebuilt import create_react_agent

from app import config
from app.agent import memory
from app.agent.tools import get_tools
from app.agent.utils import content_to_text, extract_final_answer
from app.core.llm import get_chat_model
from app.sse import sse_json

SYSTEM_PROMPT = """你是一个智能创作Agent，目标是根据用户需求完成文章创作。
你可以使用以下工具：
- generateOutline: 根据标题生成结构化大纲
- continueWrite: 根据上文续写内容
- polishText: 润色文本，优化表达
- generateSummary: 生成文章摘要
- recommendTags: 推荐文章标签
- publishArticle: 发布文章到社区

请按需逐步调用工具，每次只调用一个工具，等待结果后再决定下一步。
完成全部创作后，输出 FINAL_ANSWER，并附上完整的文章内容（Markdown格式）。
不要在 FINAL_ANSWER 之前输出完整文章。
"""


async def run_agent(goal: str, style: str | None, word_count: int | None,
                    session_id: str, user_id: int) -> AsyncIterator[str]:
    """执行 Agent 创作任务，产出与单体一致的 SSE 事件流"""
    user_text = f"创作目标：{goal}"
    if style and style.strip():
        user_text += f"\n风格要求：{style}"
    if word_count and word_count > 0:
        user_text += f"\n目标字数：约{word_count}字"
    user_message = HumanMessage(content=user_text)

    history = await memory.load_history(session_id)
    if history:
        messages = history + [user_message]
        await memory.append_messages(session_id, [user_message])
    else:
        messages = [SystemMessage(content=SYSTEM_PROMPT), user_message]
        await memory.append_messages(session_id, messages)

    graph = create_react_agent(get_chat_model(), get_tools(user_id))

    yield sse_json({"type": "thinking", "data": "开始分析创作目标..."})

    model_calls = 0
    try:
        async for update in graph.astream(
            {"messages": messages},
            config={"recursion_limit": config.AGENT_MAX_ITERATIONS * 2 + 2},
            stream_mode="updates",
        ):
            for node, payload in update.items():
                new_msgs = payload.get("messages", [])
                if node == "agent":
                    msg = new_msgs[-1] if new_msgs else None
                    if msg is None:
                        continue
                    await memory.append_messages(session_id, [msg])
                    tool_calls = getattr(msg, "tool_calls", None)
                    if tool_calls:
                        model_calls += 1
                        names = ", ".join(tc["name"] for tc in tool_calls)
                        yield sse_json({"type": "tool_start", "data": f"调用工具：{names}"})
                    else:
                        final = content_to_text(msg.content) or "创作完成，但未生成有效内容"
                        yield sse_json({"type": "final_chunk", "data": extract_final_answer(final)})
                        yield sse_json({"type": "done", "data": ""})
                        await memory.clear_session(session_id)
                        return
                elif node == "tools":
                    for tm in new_msgs:
                        await memory.append_messages(session_id, [tm])
                        text = content_to_text(tm.content)
                        if text and text.strip():
                            yield sse_json({"type": "tool_result", "data": text})
                    yield sse_json({"type": "thinking", "data": "分析工具结果，规划下一步..."})
                    if model_calls >= config.AGENT_MAX_ITERATIONS:
                        break
        # 达到最大迭代次数（或流自然结束仍无最终回答）
        yield sse_json({"type": "final_chunk", "data": "已达到最大迭代次数，创作终止。"})
        yield sse_json({"type": "done", "data": ""})
        await memory.clear_session(session_id)
    except GraphRecursionError:
        yield sse_json({"type": "final_chunk", "data": "已达到最大迭代次数，创作终止。"})
        yield sse_json({"type": "done", "data": ""})
        await memory.clear_session(session_id)
    except Exception:
        # 兜底异常交由上层 API 包装为 error + done 事件
        await memory.clear_session(session_id)
        raise
