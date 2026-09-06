"""写作助手链：continue/polish/outline 三模式提示词（自单体 WritingAssistantService 平移）"""

from langchain_core.messages import HumanMessage, SystemMessage

SYSTEM_PROMPTS = {
    "continue": "你是一个专业的内容创作者。请根据用户提供的上文，自然流畅地续写100-200字，保持风格一致。直接输出续写内容，不要加任何前缀或解释。",
    "polish": "你是一个专业的文字编辑。请优化以下文本的表达，修正语病，使其更流畅、专业，但保持原意不变。直接输出优化后的文本，不要加任何前缀或解释。",
    "outline": "你是一个结构化的写作助手。请根据用户提供的标题，生成一份详细的文章大纲，包含至少3个二级标题，每个二级标题下包含2-3个要点。使用Markdown列表格式输出。",
}
DEFAULT_PROMPT = "你是一个写作助手。"


def build_messages(type_: str, content: str, context: str | None) -> list:
    base = SYSTEM_PROMPTS.get(type_, DEFAULT_PROMPT)
    if context and context.strip():
        base += "\n\n以下是文章的其他部分作为背景参考：\n" + context

    if type_ == "continue":
        user = f"上文：\n{content}\n\n请续写："
    elif type_ == "polish":
        user = f"原文：\n{content}\n\n优化后："
    elif type_ == "outline":
        user = f"标题：{content}\n\n大纲："
    else:
        user = content

    return [SystemMessage(content=base), HumanMessage(content=user)]
