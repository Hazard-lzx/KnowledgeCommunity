"""存量文章全量重建索引：分页拉取已发布文章 → 分块 → embedding → 写入 Milvus

用法（在 ai-service 目录下）：
    python scripts/rebuild_index.py
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.chains.qa_chain import index_article  # noqa: E402
from app.core import java  # noqa: E402


async def main():
    page, size, total, failed = 1, 50, 0, []
    while True:
        articles = await java.list_published(page, size)
        if not articles:
            break
        for article in articles:
            try:
                chunks = await index_article(article)
                total += 1
                print(f"[{total}] article {article['id']} -> {chunks} chunks")
            except Exception as e:
                failed.append(article["id"])
                print(f"[FAIL] article {article['id']}: {e}")
        page += 1
    print(f"完成：共索引 {total} 篇，失败 {len(failed)} 篇 {failed if failed else ''}")
    await java.close_client()


if __name__ == "__main__":
    asyncio.run(main())
