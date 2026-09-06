"""Result<T> 契约封装与全局错误类型，对齐单体 com.knowledgecommunity.common.Result"""


def success(data=None) -> dict:
    return {"code": 200, "message": "success", "data": data}


def error(code: int, message: str) -> dict:
    return {"code": code, "message": message, "data": None}


class AppError(Exception):
    """业务异常：默认 HTTP 200 + body 内 code（对齐单体 BusinessException 契约）"""

    def __init__(self, code: int, message: str, http_status: int = 200):
        super().__init__(message)
        self.code = code
        self.message = message
        self.http_status = http_status
