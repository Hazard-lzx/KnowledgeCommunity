from contextlib import asynccontextmanager
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api import agent, internal, qa, writing
from app.core.java import close_client
from app.core.redis import close_pool
from app.core.trace import trace_id_var
from app.result import AppError, error, success


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield
    await close_client()
    await close_pool()


app = FastAPI(title="knowledge-community-ai-service", lifespan=lifespan)


@app.middleware("http")
async def trace_id_middleware(request: Request, call_next):
    trace_id = request.headers.get("X-Trace-Id", "").strip()
    if not trace_id or len(trace_id) > 64:
        trace_id = uuid4().hex
    trace_id_var.set(trace_id)
    response = await call_next(request)
    response.headers["X-Trace-Id"] = trace_id
    return response


app.include_router(qa.router)
app.include_router(writing.router)
app.include_router(agent.router)
app.include_router(internal.router)


@app.get("/health")
async def health():
    return success("ok")


@app.exception_handler(AppError)
async def app_error_handler(_: Request, exc: AppError):
    """业务异常：默认 HTTP 200 + body code（对齐单体 BusinessException 契约）"""
    return JSONResponse(status_code=exc.http_status, content=error(exc.code, exc.message))


@app.exception_handler(RequestValidationError)
async def validation_handler(_: Request, exc: RequestValidationError):
    """参数校验失败：HTTP 400 + "字段: 消息"（对齐单体 MethodArgumentNotValidException）"""
    import logging

    body = (await exc.body()) if callable(getattr(exc, "body", None)) else getattr(exc, "body", None)
    logging.getLogger("ai-service").warning("validation failed: errors=%s body=%r", exc.errors()[:2], body)
    parts = []
    for e in exc.errors()[:3]:
        field = ".".join(str(loc) for loc in e.get("loc", [])[1:]) or "参数"
        parts.append(f"{field}: {e.get('msg', '校验失败')}")
    return JSONResponse(status_code=400, content=error(400, "; ".join(parts) or "参数校验失败"))


@app.exception_handler(Exception)
async def unhandled_handler(_: Request, __: Exception):
    return JSONResponse(status_code=500, content=error(500, "服务器内部错误"))
