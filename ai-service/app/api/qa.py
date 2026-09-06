"""RAG 问答：POST /api/qa/ask → SSE 流式（data: <chunk> ... data: [DONE]）"""

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.api.deps import require_user
from app.chains.qa_chain import build_prompt, retrieve_chunks
from app.core.llm import get_chat_model
from app.core.redis import check_daily_quota
from app.sse import sse_data

router = APIRouter(prefix="/api/qa", tags=["qa"])

SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class QaRequest(BaseModel):
    articleId: int = Field(gt=0)
    question: str = Field(min_length=1)


@router.post("/ask")
async def ask(body: QaRequest, user_id: int = Depends(require_user)):
    await check_daily_quota(user_id)
    # 检索失败（文章不存在/内部错误）在流开始前抛出 → HTTP 200 + Result JSON（对齐单体契约）
    chunks = await retrieve_chunks(body.articleId, body.question)
    prompt = build_prompt(body.question, chunks)
    chat = get_chat_model()

    async def gen():
        async for chunk in chat.astream(prompt):
            text = chunk.content if isinstance(chunk.content, str) else ""
            if not text:
                continue
            yield sse_data(text)
        yield sse_data("[DONE]")

    return StreamingResponse(gen(), media_type="text/event-stream", headers=SSE_HEADERS)
