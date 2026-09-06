"""Redis：会话历史/配额计数直连（Python 不直连 MySQL，数据一律 HTTP 回调 Java）"""

import redis.asyncio as aioredis

from app import config
from app.result import AppError

_pool = aioredis.ConnectionPool.from_url(config.REDIS_URL, decode_responses=True)


def get_redis() -> aioredis.Redis:
    return aioredis.Redis(connection_pool=_pool)


async def close_pool() -> None:
    await _pool.aclose()


async def check_daily_quota(user_id: int) -> None:
    """per-user 每日配额：Redis 计数，超限抛业务异常"""
    r = get_redis()
    from datetime import date

    key = f"ai:quota:{user_id}:{date.today():%Y%m%d}"
    count = await r.incr(key)
    if count == 1:
        await r.expire(key, 86400)
    if count > config.AI_DAILY_QUOTA:
        raise AppError(429, "今日 AI 使用次数已达上限，请明天再试")
