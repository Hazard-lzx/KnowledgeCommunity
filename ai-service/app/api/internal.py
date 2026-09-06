"""内部接口（X-Internal-Token 共享密钥保护，仅供 Java 服务直连调用）

- POST /internal/index    索引事件（article-service ArticleIndexForwarder 转发）
- POST /internal/summary  文章摘要（article-service ArticlePublishedConsumer 调用）

失败必须返回非 2xx，触发 RocketMQ 重投；文章已不存在时清理向量并返回成功（防死循环重投）。
"""

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from app import config
from app.chains.qa_chain import index_article
from app.core import java, milvus
from app.core.llm import get_tool_llm
from app.result import AppError, success

router = APIRouter(prefix="/internal", tags=["internal"])


class IndexEvent(BaseModel):
    articleId: int = Field(gt=0)
    action: str = Field(min_length=1)


class SummaryRequest(BaseModel):
    content: str = Field(min_length=1)


@router.post("/index")
async def index(body: IndexEvent, x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")):
    if not config.INTERNAL_TOKEN or x_internal_token != config.INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="内部接口令牌无效")
    try:
        if body.action == "DELETE":
            await milvus.a_delete(body.articleId)
            return success()
        try:
            article = await java.get_article(body.articleId)
            chunk_count = await index_article(article)
            return success(chunk_count)
        except AppError as e:
            if e.code == 404:
                await milvus.a_delete(body.articleId)
                return success(0)
            raise
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"索引失败：{e}")


@router.post("/summary")
async def summary(body: SummaryRequest, x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")):
    """文章摘要生成（自单体 ArticlePublishedConsumer 平移；失败非 2xx 触发 MQ 重投）"""
    if not config.INTERNAL_TOKEN or x_internal_token != config.INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="内部接口令牌无效")
    try:
        content = body.content[:2000]
        text = get_tool_llm().invoke(
            [
                ("system", "你是一个专业的内容摘要生成器，请用简洁的中文总结以下文章的核心内容，不超过200字。"),
                ("human", content),
            ]
        ).content
        return success(text)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"摘要生成失败：{e}")
