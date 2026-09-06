def content_to_text(content) -> str:
    """LangChain 消息 content 可能是 str 或分段 list，统一转纯文本"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for part in content:
            if isinstance(part, str):
                parts.append(part)
            elif isinstance(part, dict) and isinstance(part.get("text"), str):
                parts.append(part["text"])
        return "".join(parts)
    return str(content)


def extract_final_answer(text: str) -> str:
    """对齐单体：截取 FINAL_ANSWER 标记之后的内容，去掉开头的冒号"""
    if "FINAL_ANSWER" in text:
        result = text.split("FINAL_ANSWER", 1)[1].strip()
        if result.startswith(":") or result.startswith("："):
            result = result[1:].strip()
        return result
    return text
