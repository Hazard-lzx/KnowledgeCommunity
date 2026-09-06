"""SSE 事件格式工具：三种输出契约全部对齐单体实现"""


def sse_data(data: str) -> str:
    """RAG 问答：纯 data 行（data: <chunk>），前端按行解析"""
    return f"data: {data}\n\n"


def sse_event(event: str, data: str) -> str:
    """写作助手：event: <type> + data: <content>"""
    return f"event: {event}\ndata: {data}\n\n"


def sse_json(payload: dict) -> str:
    """Agent：data: {"type":"...","data":"..."} JSON 事件"""
    import json

    return "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"
