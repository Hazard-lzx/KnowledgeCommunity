"""MQ 索引链路端到端验证：经网关发布文章 → 等待 MQ 转发 → 查询 Milvus 确认向量入库"""
import json
import time
import urllib.request

GW = "http://localhost:9000"
TITLE = f"MQ索引链路验证 {int(time.time())}"
CONTENT = """## 概述
这是用于验证消息队列索引链路的测试文章，包含多个二级标题段落。

## 核心内容
文章发布后，单体发送 ARTICLE_INDEX_EVENT 到 RocketMQ，ArticleIndexForwarder 消费并转发给 ai-service，由 Python 侧分块、嵌入并写入 Milvus 向量库。

## 验证要点
验证完成后可在 Milvus 的 article_chunks collection 中按 article_id 检索到对应向量。
"""


def gw_login():
    u = f"e2e{int(time.time())}"
    req = urllib.request.Request(
        f"{GW}/api/auth/register",
        data=json.dumps({"username": u, "password": "Test123456", "email": f"{u}@test.com"}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    urllib.request.urlopen(req, timeout=15).read()
    req = urllib.request.Request(
        f"{GW}/api/auth/login",
        data=json.dumps({"username": u, "password": "Test123456"}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=15).read())["data"]["accessToken"]


def create_article(token):
    req = urllib.request.Request(
        f"{GW}/api/articles",
        data=json.dumps({"title": TITLE, "content": CONTENT, "tags": ["测试"], "status": 1}).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"}, method="POST")
    body = json.loads(urllib.request.urlopen(req, timeout=15).read())
    return body["data"]


def check_milvus(article_id, retries=12, delay=5):
    from pymilvus import MilvusClient
    client = MilvusClient(uri="http://192.168.80.80:19530")
    for i in range(retries):
        res = client.query(
            collection_name="article_chunks",
            filter=f"article_id == {article_id}",
            output_fields=["chunk_index", "text"])
        if res:
            return res
        time.sleep(delay)
    return []


if __name__ == "__main__":
    token = gw_login()
    article_id = create_article(token)
    print(f"article created: id={article_id}, title={TITLE}")
    rows = check_milvus(article_id)
    if rows:
        print(f"[PASS] MQ index pipeline: {len(rows)} chunks in Milvus for article {article_id}")
        for r in sorted(rows, key=lambda x: x["chunk_index"]):
            print(f"  chunk {r['chunk_index']}: {r['text'][:60]}...")
    else:
        print(f"[FAIL] no vectors found in Milvus for article {article_id}")
