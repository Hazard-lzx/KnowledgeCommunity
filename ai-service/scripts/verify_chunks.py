from pymilvus import MilvusClient

client = MilvusClient(uri="http://192.168.80.80:19530")
for aid in (32, 33):
    rows = client.query(
        collection_name="article_chunks",
        filter=f"article_id == {aid}",
        output_fields=["chunk_index", "text"],
    )
    rows = sorted(rows, key=lambda x: x["chunk_index"])
    print(f"article {aid}: {len(rows)} chunks")
    for r in rows[:3]:
        idx = r["chunk_index"]
        text = r["text"][:50]
        print(f"  chunk {idx}: {text}...")
