"""全链路 traceId 上下文（contextvar），供 main middleware 与 httpx 回调透传共用"""

from contextvars import ContextVar

trace_id_var: ContextVar[str] = ContextVar("trace_id", default="-")


def get_trace_id() -> str:
    return trace_id_var.get()
