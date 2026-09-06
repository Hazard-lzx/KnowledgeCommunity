"""RAG 问答链：分块（对齐单体算法）→ embedding → Milvus top-3 检索 → 拼接 Prompt"""

import asyncio
import re

from app import config
from app.core import java, milvus
from app.core.llm import get_embeddings


def split_into_chunks(content: str) -> list[str]:
    """按 ## Markdown 标题分块，累积不超过 500 字（与单体 AiService 完全一致）"""
    sections = [s for s in re.split(r"(?=##)", content or "") if s.strip()]
    chunks: list[str] = []
    current = ""
    for section in sections:
        if len(current) + len(section) > config.CHUNK_MAX_LENGTH and current:
            chunks.append(current.strip())
            current = ""
        current += section
    if current:
        chunks.append(current.strip())
    return chunks


async def index_article(article: dict) -> int:
    """文章分块 → embedding → 全量替换该文章向量，返回块数"""
    chunks = split_into_chunks(article.get("content") or "")
    if not chunks:
        await milvus.a_delete(article["id"])
        return 0
    vectors = await asyncio.to_thread(get_embeddings().embed_documents, chunks)
    await milvus.a_delete(article["id"])
    await milvus.a_insert(article["id"], chunks, vectors)
    return len(chunks)


async def retrieve_chunks(article_id: int, question: str) -> list[str]:
    """问题 embedding → Milvus top-3；命中为空时回源 Java 懒索引（兼容未回填的存量文章）"""
    query_vector = await asyncio.to_thread(get_embeddings().embed_query, question)
    hits = await milvus.a_search(article_id, query_vector)
    if not hits:
        article = await java.get_article(article_id)
        if article.get("content"):
            await index_article(article)
            hits = await milvus.a_search(article_id, query_vector)
    return hits


def build_prompt(question: str, chunks: list[str]) -> str:
    context = "\n\n".join(chunks)
    return f"根据以下参考内容回答问题：\n{context}\n\n问题：{question}\n回答："
