from fastapi import Header

from app.result import AppError


def require_user(x_user_id: str | None = Header(default=None, alias="X-User-Id")) -> int:
    """只信网关注入的身份头（网关已剥离外部伪造的同名头），不解析 JWT"""
    if not x_user_id or not x_user_id.strip().isdigit():
        raise AppError(401, "未登录或登录已过期", http_status=401)
    return int(x_user_id.strip())
