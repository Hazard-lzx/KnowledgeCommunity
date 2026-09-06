# ai-service

知识社区 AI 服务（Python FastAPI + LangChain/LangGraph），承接单体 AI 三件套：

| 能力 | 接口 | SSE 契约 |
|---|---|---|
| RAG 问答 | `POST /api/qa/ask` | `data: <chunk>` … `data: [DONE]` |
| 写作助手 | `POST /api/ai/writing-assist` | `event: chunk/done/error` |
| 创作 Agent | `POST /api/ai/agent/create` | `data: {"type": "thinking/tool_start/tool_result/final_chunk/done"}` |
| 索引入口（内部） | `POST /internal/index` | 单体 ArticleIndexForwarder 转发 MQ 索引事件 |

架构约束：
- 只信网关注入的 `X-User-Id` 身份头，不解析 JWT；`/internal/**` 校验 `X-Internal-Token`
- 不直连 MySQL，文章数据一律 HTTP 回调 Java 内部接口获取
- Redis 直连：Agent 会话（2h TTL、超 20 条压缩）与 per-user 每日配额
- 向量存储 Milvus（`text-embedding-v3`，1024 维，HNSW + COSINE，top-3）
- chat 模型 `deepseek-v3`；模型名与维度锁在 `app/config.py` 单一常量

## 启动

```powershell
cd ai-service
pip install -r requirements.txt
copy .env.example .env   # 填入真实密钥（.env 已 gitignore）
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 存量文章回填 / 旧数据清理

```powershell
python scripts/rebuild_index.py     # 全量拉取已发布文章重建 Milvus 索引
python scripts/cleanup_old_rag.py   # 清理旧版 Redis RAG 数据（article:rag:*）
```

## 回滚方式

网关将 `/api/ai/**`、`/api/qa/**` 路由切回单体（`http://localhost:8080`）即可，单体旧 AI 模块代码保留一个版本周期。
