"""Milvus 向量库：1024 维 collection（HNSW + COSINE），懒初始化"""

import asyncio
import threading

from pymilvus import DataType, MilvusClient

from app import config

_client: MilvusClient | None = None
_lock = threading.Lock()


def _get_client() -> MilvusClient:
    global _client
    if _client is None:
        with _lock:
            if _client is None:
                client = MilvusClient(uri=config.MILVUS_URI)
                _ensure_collection(client)
                _client = client
    return _client


def _ensure_collection(client: MilvusClient) -> None:
    if client.has_collection(config.MILVUS_COLLECTION):
        return
    schema = client.create_schema(auto_id=True, enable_dynamic_field=False)
    schema.add_field("id", DataType.INT64, is_primary=True)
    schema.add_field("article_id", DataType.INT64)
    schema.add_field("chunk_index", DataType.INT64)
    schema.add_field("text", DataType.VARCHAR, max_length=65535)
    schema.add_field("vector", DataType.FLOAT_VECTOR, dim=config.EMBEDDING_DIM)
    index_params = client.prepare_index_params()
    index_params.add_index(
        field_name="vector",
        index_type="HNSW",
        metric_type="COSINE",
        params={"M": 16, "efConstruction": 200},
    )
    client.create_collection(
        collection_name=config.MILVUS_COLLECTION,
        schema=schema,
        index_params=index_params,
        consistency_level="Strong",
    )


def delete_article(article_id: int) -> None:
    _get_client().delete(config.MILVUS_COLLECTION, filter=f"article_id == {article_id}")


def insert_chunks(article_id: int, chunks: list[str], vectors: list[list[float]]) -> None:
    rows = [
        {"article_id": article_id, "chunk_index": i, "text": text[:20000], "vector": vec}
        for i, (text, vec) in enumerate(zip(chunks, vectors))
    ]
    if rows:
        _get_client().insert(config.MILVUS_COLLECTION, rows)


def search(article_id: int, query_vector: list[float]) -> list[str]:
    res = _get_client().search(
        collection_name=config.MILVUS_COLLECTION,
        data=[query_vector],
        limit=config.RAG_TOP_K,
        filter=f"article_id == {article_id}",
        output_fields=["text"],
    )
    return [hit["entity"]["text"] for hit in res[0]]


async def a_delete(article_id: int) -> None:
    await asyncio.to_thread(delete_article, article_id)


async def a_insert(article_id: int, chunks: list[str], vectors: list[list[float]]) -> None:
    await asyncio.to_thread(insert_chunks, article_id, chunks, vectors)


async def a_search(article_id: int, query_vector: list[float]) -> list[str]:
    return await asyncio.to_thread(search, article_id, query_vector)
