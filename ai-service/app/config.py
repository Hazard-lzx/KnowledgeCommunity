import os

from dotenv import load_dotenv

load_dotenv()


def _require(name: str) -> str:
    value = os.getenv(name, "")
    if not value:
        raise RuntimeError(f"缺少必需的环境变量 {name}，请在 ai-service/.env 中配置（参考 .env.example）")
    return value


# ===== 模型常量（唯一事实来源，collection schema 与 embedding 调用共用，禁止别处写死）=====
CHAT_MODEL = "deepseek-v3"
EMBEDDING_MODEL = "text-embedding-v3"
EMBEDDING_DIM = 1024

# langchain-openai 的 base_url 需带 /v1（OpenAI SDK 只在其后拼 /chat/completions；
# 单体 Spring AI 的 base-url 不带 /v1，因为它自动拼 /v1/chat/completions）
DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

AI_API_KEY = _require("AI_API_KEY")
INTERNAL_TOKEN = _require("INTERNAL_TOKEN")
# article-service 内部接口（文章回源/发布回调；单体退役后由 8102 承接）
JAVA_BASE_URL = os.getenv("JAVA_BASE_URL", "http://localhost:8102")
MILVUS_URI = os.getenv("MILVUS_URI", "http://192.168.80.80:19530")
REDIS_URL = _require("REDIS_URL")

# ===== Agent 行为约束（自单体平移）=====
AGENT_MAX_ITERATIONS = 15
SESSION_TTL_HOURS = 2
SESSION_MAX_MESSAGES = 20
SESSION_KEEP_RECENT = 10

# ===== RAG 分块与检索（自单体平移）=====
CHUNK_MAX_LENGTH = 500
RAG_TOP_K = 3
MILVUS_COLLECTION = "article_chunks"

# ===== per-user 每日配额（Python 内 Redis 计数）=====
AI_DAILY_QUOTA = int(os.getenv("AI_DAILY_QUOTA", "50"))
