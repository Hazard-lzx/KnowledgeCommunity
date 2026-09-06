"""创作 Agent：POST /api/ai/agent/create → SSE（data: {"type":..,"data":..} JSON 事件流）"""

import uuid

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.agent import memory
from app.agent.graph import run_agent
from app.api.deps import require_user
from app.core.redis import check_daily_quota
from app.sse import sse_json

router = APIRouter(prefix="/api/ai/agent", tags=["agent"])

SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class AgentCreateRequest(BaseModel):
    goal: str = Field(min_length=1)
    style: str | None = None
    wordCount: int | None = Field(default=None, gt=0)


@router.post("/create")
async def create(body: AgentCreateRequest, user_id: int = Depends(require_user)):
    await check_daily_quota(user_id)
    session_id = str(uuid.uuid4())

    async def gen():
        try:
            async for event in run_agent(body.goal, body.style, body.wordCount, session_id, user_id):
                yield event
        except Exception as e:
            yield sse_json({"type": "error", "data": f"执行异常：{e}"})
            yield sse_json({"type": "done", "data": ""})
            await memory.clear_session(session_id)

    return StreamingResponse(gen(), media_type="text/event-stream", headers=SSE_HEADERS)
