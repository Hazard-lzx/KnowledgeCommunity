"""Agent 工具集：5 个 LLM 工具 + 1 个 Java 回调发布工具，@tool 装饰器模式（自单体 tools 包平移）"""

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.tools import tool

from app.core import java
from app.core.llm import get_tool_llm

OUTLINE_SYSTEM = "你是一个结构化的写作助手。请根据用户提供的标题，生成一份详细的文章大纲，包含至少3个二级标题，每个二级标题下包含2-3个要点。使用 Markdown 列表格式输出。直接输出大纲，不要加任何前缀或解释。"
CONTINUE_SYSTEM = "你是一个专业的内容创作者。请根据用户提供的上文，自然流畅地续写 100-200 字，保持风格一致。直接输出续写内容，不要加任何前缀或解释。"
POLISH_SYSTEM = "你是一个专业的文字编辑。请优化以下文本的表达，修正语病，使其更流畅、专业，但保持原意不变。直接输出优化后的文本，不要加任何前缀或解释。"
SUMMARY_SYSTEM = "你是一个专业的内容编辑。请根据用户提供的文章内容，生成一份200字以内的摘要，提炼核心观点。直接输出摘要，不要加任何前缀或解释。"
TAGS_SYSTEM = "你是一个内容标签专家。请根据用户提供的文章内容，推荐3-5个合适的标签。只返回逗号分隔的标签字符串，不要加任何前缀、解释或编号。例如：Spring Boot,微服务,Java"


def _invoke(system: str, user: str) -> str:
    resp = get_tool_llm().invoke([SystemMessage(content=system), HumanMessage(content=user)])
    return resp.content if isinstance(resp.content, str) else str(resp.content)


@tool
def generate_outline(title: str) -> str:
    """根据标题生成文章结构化大纲"""
    return _invoke(OUTLINE_SYSTEM, f"标题：{title}\n\n大纲：")


@tool
def continue_write(context: str) -> str:
    """根据上文续写文章内容"""
    return _invoke(CONTINUE_SYSTEM, f"上文：\n{context}\n\n请续写：")


@tool
def polish_text(text: str) -> str:
    """润色文本，优化表达方式"""
    return _invoke(POLISH_SYSTEM, f"原文：\n{text}\n\n优化后：")


@tool
def generate_summary(content: str) -> str:
    """根据内容生成文章摘要"""
    return _invoke(SUMMARY_SYSTEM, f"文章内容：\n{content}\n\n摘要：")


@tool
def recommend_tags(content: str) -> str:
    """根据内容推荐文章标签"""
    return _invoke(TAGS_SYSTEM, f"文章内容：\n{content}\n\n推荐标签：")


def make_publish_tool(user_id: int):
    """发布工具工厂：闭包绑定当前用户，回调 Java 内部接口（X-Internal-Token + X-User-Id）"""

    @tool
    def publish_article(title: str, content: str, summary: str, tags: str) -> str:
        """发布文章到社区"""
        try:
            tag_list = [t.strip() for t in tags.split(",") if t.strip()] if tags else None
            article_id = java.publish_article_sync(user_id, title, content, tag_list)
            return f"文章发布成功！文章ID：{article_id}，标题：{title}"
        except Exception as e:
            return f"发布失败：{e}"

    return publish_article


def get_tools(user_id: int) -> list:
    return [generate_outline, continue_write, polish_text, generate_summary, recommend_tags, make_publish_tool(user_id)]
