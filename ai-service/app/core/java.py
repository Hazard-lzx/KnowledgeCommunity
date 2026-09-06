"""Java 服务内部接口回调（httpx，带 X-Internal-Token；publish 另带 X-User-Id 表示代表该用户行事）"""

import httpx

from app import config
from app.core.trace import get_trace_id
from app.result import AppError

_client: httpx.AsyncClient | None = None


def _headers(extra: dict | None = None) -> dict:
    headers = {"X-Internal-Token": config.INTERNAL_TOKEN}
    trace_id = get_trace_id()
    if trace_id and trace_id != "-":
        headers["X-Trace-Id"] = trace_id
    if extra:
        headers.update(extra)
    return headers


def get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(base_url=config.JAVA_BASE_URL, timeout=15)
    return _client


async def close_client() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None


async def _call(method: str, url: str, **kwargs) -> object:
    try:
        resp = await get_client().request(method, url, headers=_headers(kwargs.pop("extra_headers", None)), **kwargs)
    except httpx.HTTPError as e:
        raise AppError(500, f"Java 服务调用失败：{e.__class__.__name__}")
    if resp.status_code != 200:
        raise AppError(500, f"Java 内部接口返回 HTTP {resp.status_code}")
    body = resp.json()
    if body.get("code") != 200:
        raise AppError(body.get("code", 500), body.get("message", "Java 内部接口调用失败"))
    return body.get("data")


async def get_article(article_id: int) -> dict:
    return await _call("GET", f"/api/internal/articles/{article_id}")


async def list_published(page: int, size: int) -> list:
    return await _call("GET", "/api/internal/articles/published", params={"page": page, "size": size})


async def publish_article(user_id: int, title: str, content: str, tags: list[str] | None) -> int:
    return await _call(
        "POST",
        "/api/internal/articles/publish",
        json={"title": title, "content": content, "tags": tags},
        extra_headers={"X-User-Id": str(user_id)},
    )


def publish_article_sync(user_id: int, title: str, content: str, tags: list[str] | None) -> int:
    """同步版：供 Agent 工具（线程池内执行）回调，独立短超时防止单工具卡死循环"""
    with httpx.Client(base_url=config.JAVA_BASE_URL, timeout=15) as client:
        resp = client.post(
            "/api/internal/articles/publish",
            json={"title": title, "content": content, "tags": tags},
            headers=_headers({"X-User-Id": str(user_id)}),
        )
    if resp.status_code != 200:
        raise RuntimeError(f"Java 内部接口返回 HTTP {resp.status_code}")
    body = resp.json()
    if body.get("code") != 200:
        raise RuntimeError(body.get("message", "发布失败"))
    return body.get("data")
