"""写作助手：POST /api/ai/writing-assist → SSE（event: chunk/done/error）"""

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.api.deps import require_user
from app.chains.writing_chain import build_messages
from app.core.llm import get_chat_model
from app.core.redis import check_daily_quota
from app.sse import sse_event

router = APIRouter(prefix="/api/ai", tags=["writing"])

SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class WritingAssistRequest(BaseModel):
    type: str = Field(min_length=1)
    content: str = Field(min_length=1)
    context: str | None = None


@router.post("/writing-assist")
async def writing_assist(body: WritingAssistRequest, user_id: int = Depends(require_user)):
    await check_daily_quota(user_id)
    messages = build_messages(body.type, body.content, body.context)
    chat = get_chat_model()

    async def gen():
        has_content = False
        try:
            async for chunk in chat.astream(messages):
                text = chunk.content if isinstance(chunk.content, str) else ""
                if not text:
                    continue
                has_content = True
                yield sse_event("chunk", text)
            if not has_content:
                yield sse_event("chunk", "暂无内容返回")
            yield sse_event("done", "[DONE]")
        except Exception as e:
            yield sse_event("error", str(e))

    return StreamingResponse(gen(), media_type="text/event-stream", headers=SSE_HEADERS)
