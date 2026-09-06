from functools import lru_cache

from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from app import config


@lru_cache
def get_chat_model() -> ChatOpenAI:
    """流式对话模型（RAG 问答 / 写作助手）"""
    return ChatOpenAI(
        model=config.CHAT_MODEL,
        api_key=config.AI_API_KEY,
        base_url=config.DASHSCOPE_BASE_URL,
        temperature=0.7,
        streaming=True,
        timeout=120,
        max_retries=1,
    )


@lru_cache
def get_tool_llm() -> ChatOpenAI:
    """Agent 工具内非流式调用（与单体 ChatClient.call() 对应）"""
    return ChatOpenAI(
        model=config.CHAT_MODEL,
        api_key=config.AI_API_KEY,
        base_url=config.DASHSCOPE_BASE_URL,
        temperature=0.7,
        timeout=60,
        max_retries=1,
    )


@lru_cache
def get_embeddings() -> OpenAIEmbeddings:
    # text-embedding-v3 默认 1024 维，不传 dimensions（维度锁在 config.EMBEDDING_DIM）
    return OpenAIEmbeddings(
        model=config.EMBEDDING_MODEL,
        api_key=config.AI_API_KEY,
        base_url=config.DASHSCOPE_BASE_URL,
        check_embedding_ctx_length=False,
        timeout=60,
        max_retries=1,
    )
