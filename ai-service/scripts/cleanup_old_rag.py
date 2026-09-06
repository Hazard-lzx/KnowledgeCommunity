"""清理旧版 Redis RAG 数据（article:rag:*）：RAG 已迁移 Milvus，确认新链路正常后执行一次

用法（在 ai-service 目录下）：
    python scripts/cleanup_old_rag.py
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import redis.asyncio as aioredis  # noqa: E402

from app import config  # noqa: E402

PATTERNS = ["article:rag:indexed*", "article:rag:chunk*", "article:rag:chunkcount*"]


async def main():
    r = aioredis.Redis.from_url(config.REDIS_URL, decode_responses=True)
    total = 0
    for pattern in PATTERNS:
        cursor = 0
        while True:
            cursor, keys = await r.scan(cursor, match=pattern, count=500)
            if keys:
                await r.delete(*keys)
                total += len(keys)
            if cursor == 0:
                break
    await r.aclose()
    print(f"已清理 {total} 个旧 RAG 键（article:rag:*）")


if __name__ == "__main__":
    asyncio.run(main())
