# KnowledgeCommunity · AI 知识社区（v2.0 微服务架构）

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2023.0.3-6DB33F?style=flat&logo=spring" alt="Spring Cloud">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/Python-3.11+-3776AB?style=flat&logo=python" alt="Python">
  <img src="https://img.shields.io/badge/FastAPI-0.115-009688?style=flat&logo=fastapi" alt="FastAPI">
  <img src="https://img.shields.io/badge/LangChain-0.3-1C3C3C?style=flat&logo=langchain" alt="LangChain">
  <img src="https://img.shields.io/badge/Nacos-2.x-FF6A00?style=flat&logo=alibabacloud" alt="Nacos">
  <img src="https://img.shields.io/badge/Milvus-2.4-00A1EA?style=flat&logo=milvus" alt="Milvus">
  <img src="https://img.shields.io/badge/Vue%203-4FC08D?style=flat&logo=vuedotjs" alt="Vue 3">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis" alt="Redis">
  <img src="https://img.shields.io/badge/Elasticsearch-8.0-005571?style=flat&logo=elasticsearch" alt="Elasticsearch">
  <img src="https://img.shields.io/badge/RocketMQ-5.x-D77310?style=flat&logo=apacherocketmq" alt="RocketMQ">
  <img src="https://img.shields.io/badge/Vite-5.2-646CFF?style=flat&logo=vite" alt="Vite">
</p>

## 项目简介

**KnowledgeCommunity** 是一个融合 AI 能力的现代化知识社区平台，支持用户发布技术文章、智能问答、个性化 Feed 流，并提供 AI 写作助手和可自定义的 AI Agent 智能创作系统。

v2.0 版本已从单体架构升级为 **Spring Cloud 微服务架构**：后端按业务域拆分为 auth / article / search / feed 四个 Java 服务，通过 **Spring Cloud Gateway** 统一入口与鉴权、**Nacos** 注册发现、**OpenFeign** 服务间调用（Sentinel 熔断降级）；AI 能力整体迁移至独立的 **Python ai-service**（FastAPI + LangChain/LangGraph），向量检索由 Redis 切换为 **Milvus**。前端保持 Vue 3 单页应用，**零改动**平滑接入新架构。

---

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│           Vue 3 + Element Plus + Pinia               │
│         (Vite 5 构建 · SPA · SSE 流式渲染)            │
└─────────────────────┬───────────────────────────────┘
                      │ HTTP / REST / SSE (dev proxy → :9000)
┌─────────────────────▼───────────────────────────────┐
│        Spring Cloud Gateway :9000                    │
│   JWT 统一鉴权 · Redis 令牌桶限流 · traceId 注入       │
│   剥离外部伪造身份头 · /api/internal/** 封锁           │
└──┬──────────┬──────────┬──────────┬──────────┬──────┘
   │ lb://    │ lb://    │ lb://    │ lb://    │ 直连
┌──▼─────┐ ┌──▼──────┐ ┌─▼───────┐ ┌▼────────┐ ┌▼──────────────┐
│  auth  │ │ article │ │ search  │ │  feed   │ │  ai-service   │
│ :8101  │ │  :8102  │ │  :8103  │ │  :8104  │ │   :8000       │
│        │ │         │ │         │ │         │ │ FastAPI       │
│ 注册登录│ │ 文章CRUD │ │ ES 检索  │ │ 三级缓存 │ │ LangChain     │
│ 用户资料│ │ 点赞收藏 │ │ 深分页   │ │ 单飞锁   │ │ LangGraph     │
│ 关注/Out│ │ 计数任务 │ │ Feign   │ │ Feign   │ │ RAG·写作·Agent│
│ OSS    │ │ MQ消费  │ │ 聚合补全 │ │ 聚合    │ │ Milvus 检索   │
└──┬─────┘ └──┬──────┘ └─┬───────┘ └┬────────┘ └──┬────────────┘
   │          │          │          │             │
   │  OpenFeign + X-Internal-Token + Sentinel 熔断  │
   │          │          │          │             │
┌──▼──────────▼──────────▼──────────▼─────────────┐ ┌▼──────────────┐
│              Nacos 注册中心 :8848                  │ │  阿里云百炼     │
└──────────────────────────────────────────────────┘ │ deepseek-v3   │
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │ embedding-v3  │
│  MySQL   │ │  Redis   │ │Elastic-  │ │ RocketMQ  │ └───────────────┘
│  8.0     │ │  7.x     │ │search 8.x│ │  5.x      │      ▲
└──────────┘ └──────────┘ └──────────┘ └───────────┘      │
┌──────────────────────────────────────────────┐          │
│  Milvus 2.4（1024 维向量 · HNSW 检索）          │──────────┘
└──────────────────────────────────────────────┘
```

**关键链路：**
- **统一入口**：前端所有请求经 Gateway（JWT 解析 → Redis `token:valid:{jti}` 白名单校验 → 注入 `X-User-Id / X-Username` 身份头），下游服务只信网关头、不再解析 JWT
- **服务间调用**：OpenFeign（Nacos 负载均衡）+ `X-Internal-Token` 共享密钥 + Sentinel 熔断（异常比例 >50% 熔断 10s）+ fallbackFactory 优雅降级
- **AI 索引链路**：文章发布 → RocketMQ → article-service 消费 → ai-service 分块嵌入写 Milvus + 生成 AI 摘要回填
- **全链路追踪**：Gateway 生成 `X-Trace-Id` → 四服务 MDC 日志 `[服务名,traceId]` → Feign / httpx 透传 → 响应头回传

---

## 核心功能

### 1. 用户体系 & 内容管理
- 注册 / 登录 / 登出（网关 JWT 统一鉴权，BCrypt 密码加密，登出即拉黑）
- 技术文章发布、编辑、删除、详情浏览
- 用户个人主页、资料编辑、关注/取关（Outbox 模式保证关注事件可靠投递）
- 文章点赞/收藏（Redis Bitmap + Lua 原子操作，定时任务异步落库）

### 2. 个性化 Feed 流
- **全站模式** / **关注模式** 双模式切换
- **三级缓存**：L1 Caffeine（5s）→ L2 Redis 页面缓存（30s）→ L3 Redis 片段缓存 + 热点探测
- 单飞锁防缓存击穿，游标分页 + Intersection Observer 无限滚动

### 3. 全文检索
- **Elasticsearch** 索引文章标题 + 内容（标题 match + wildcard 并集，dis_max 融合）
- function_score 相关性 + 点赞数加权排序，search_after 深分页
- Feign 跨服务补全作者信息、实时计数与点赞收藏状态

### 4. AI RAG 问答
- 基于文章内容的 RAG 智能问答（Python ai-service）
- 文章按 `##` 标题分块（≤500 字符）→ text-embedding-v3 向量化 → **Milvus HNSW** Top-3 检索
- **SSE 流式响应**，前端实时逐字显示，契约与单体时代完全一致

### 5. AI 写作助手

| 功能 | 说明 |
|------|------|
| 续写 | 根据已有内容自动续写段落 |
| 大纲生成 | 根据主题自动生成文章大纲 |
| 润色 | 对选定文本进行风格优化 |
| 标签推荐 | 自动生成内容标签 |
| 智能摘要 | 提取文章核心摘要 |

### 6. AI Agent 智能创作
可自定义的智能体系统（LangGraph 原生 ReAct 循环，15 轮上限），每个 Agent 拥有独立：
- **身份配置**：名称、头像、角色设定
- **工具编排**：续写、润色、摘要、大纲、标签推荐、一键发布（工具回调带 `X-Internal-Token` + 15s 独立超时）
- **记忆服务**：对话历史 Redis 持久化（2h TTL，超 20 条自动压缩）
- **SSE 事件流**：`thinking / tool_start / tool_result / final_chunk / done` 五类事件

---

## 技术栈

### 后端（Java 微服务）
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Cloud | 2023.0.3 | 微服务治理（Gateway / OpenFeign / LoadBalancer） |
| Spring Cloud Alibaba | 2023.0.1.3 | Nacos 注册发现 + Sentinel 熔断限流 |
| Spring Security | 6.x | 服务侧网关身份头认证 |
| MyBatis-Plus | 3.5.6 | ORM / MySQL 操作 |
| Spring Data Redis | - | 缓存 / 计数 / Bitmap / 分布式锁 |
| Spring Data Elasticsearch | - | 全文检索 |
| RocketMQ | 2.3.0 | 异步消息（索引事件、关注事件、摘要生成） |
| Caffeine | - | 本地缓存（Feed L1） |
| JJWT | 0.12.5 | JWT 令牌生成与验证（网关侧） |
| Alibaba OSS | 3.17.4 | 文件上传存储 |

### AI 服务（Python）
| 技术 | 用途 |
|------|------|
| FastAPI + Uvicorn | AI 服务 HTTP 框架（SSE 流式） |
| LangChain | RAG 问答链 / 写作助手链 |
| LangGraph | Agent 原生 ReAct 循环 |
| LangChain-OpenAI | DashScope OpenAI-compatible 接入 |
| PyMilvus | Milvus 向量库客户端 |
| httpx | 回调 Java 内部接口（工具执行） |

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.4 | 前端框架 |
| Vite | 5.2 | 构建工具 |
| Element Plus | 2.6 | UI 组件库 |
| Pinia | 2.1 | 状态管理 |
| Vue Router | 4.3 | 路由管理 |
| Axios | 1.6 | HTTP 请求 |
| @kangc/v-md-editor | 2.0 | Markdown 编辑器与预览 |
| SCSS | - | CSS 预处理 |
| SSE | - | AI 流式响应 |

### AI 模型（阿里云百炼 DashScope）
| 模型 | 用途 |
|------|------|
| deepseek-v3 | 聊天 & 写作 & Agent 推理 |
| text-embedding-v3 | 文本嵌入（1024 维） |

### 数据库 & 中间件
| 组件 | 用途 |
|------|------|
| MySQL 8.0 | 业务数据持久化 |
| Redis 7.x | 缓存 / 计数 / Bitmap / 会话限流 / 令牌白名单 |
| Elasticsearch 8.x | 全文搜索 |
| Milvus 2.4 | RAG 向量存储与检索（HNSW） |
| RocketMQ 5.x | 异步消息解耦（索引 / 摘要 / 关注事件） |
| Nacos 2.x | 服务注册发现 |

---

## 项目结构

```
knowledge-community/
├── backend/                              # Maven 多模块（父 pom）
│   ├── common/                           # 跨服务公共模块
│   │   └── com.knowledgecommunity
│   │       ├── common/                   # Result / BusinessException / 全局异常
│   │       │   ├── dto/                  # 跨服务契约（UserBrief/ArticleBrief/UserArticleStats/FeedItem）
│   │       │   ├── feign/                # InternalFeignConfig（内部令牌 + 身份/traceId 透传）
│   │       │   ├── sentinel/             # SentinelRuleConfig（Feign 熔断规则）
│   │       │   └── trace/                # TraceIdFilter（MDC）
│   │       └── security/                 # GatewayHeaderAuthFilter / InternalTokenFilter / UserPrincipal
│   ├── gateway/                          # Spring Cloud Gateway :9000
│   │   └── filter/AuthGlobalFilter       # JWT 校验 + 身份头注入 + traceId + 限流
│   ├── auth-service/                     # 认证与用户 :8101
│   │   └── auth/                         # 注册/登录/JWT、用户资料、关注（Outbox）、OSS
│   ├── article-service/                  # 文章与互动 :8102
│   │   └── article/                      # 文章 CRUD、点赞收藏（Bitmap+Lua）、计数任务、MQ 消费
│   ├── search-service/                   # 检索 :8103
│   │   └── search/                       # ES 检索 + function_score + search_after 深分页
│   ├── feed-service/                     # Feed 流 :8104
│   │   └── feed/                         # 三级缓存 + 单飞锁 + 下游聚合
│   └── pom.xml                           # 父 pom（common + gateway + 四业务服务）
│
├── ai-service/                           # Python AI 服务 :8000
│   ├── app/
│   │   ├── api/                          # qa / writing / agent / internal（摘要）路由
│   │   ├── chains/                       # RAG 问答链 / 写作助手链
│   │   ├── agent/                        # LangGraph ReAct 循环 / 工具 / 记忆管理
│   │   ├── core/                         # llm / milvus / redis / java 回调 / traceId
│   │   ├── config.py                     # 模型常量（deepseek-v3 / text-embedding-v3）
│   │   ├── result.py                     # Result<T> 契约封装
│   │   └── sse.py                        # SSE 事件封装
│   ├── scripts/                          # 存量回填 / 冒烟测试脚本
│   ├── requirements.txt
│   └── .env.example                      # 环境变量模板
│
├── frontend/                             # Vue 3 前端（结构未变）
│   └── src/
│       ├── views/                        # 页面组件（登录/注册/Feed/文章/搜索/问答/发布/个人主页/Agent/学习）
│       ├── components/                   # layout / article / ai / user / common
│       ├── composables/                  # 组合式函数（认证/Feed/无限滚动/点赞/SSE）
│       ├── stores/                       # Pinia（auth / feed）
│       ├── api/                          # Axios 封装
│       └── router/                       # 路由（含导航守卫）
│
├── 微服务改造-验收与回滚手册.md            # 四步验收结果 + R1~R4 回滚方案 + 超时台账
└── .gitignore
```

---

## 本地运行

### 前置条件
- JDK 17+，Maven 3.8+
- Python 3.11+
- Node.js 18+
- MySQL 8.0+，Redis 7.x，Elasticsearch 8.x，RocketMQ 5.x
- **Nacos 2.x**（服务注册发现，端口 8848）
- **Milvus 2.4**（RAG 向量库，端口 19530）

### 1. 启动基础设施

确保以下中间件已启动：
- **MySQL**：端口 3306，数据库 `knowledge_community`
- **Redis**：端口 6379
- **Elasticsearch**：端口 9200
- **RocketMQ**：端口 9876
- **Nacos**：端口 8848
- **Milvus**：端口 19530

### 2. 配置环境变量

Java 侧（各服务 `application-dev.yml` 已 gitignore，缺失时参考 `application.yml` 中的变量占位）：

| 变量名 | 说明 |
|--------|------|
| `DB_PASSWORD` | MySQL 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `ES_PASSWORD` | Elasticsearch 密码 |
| `INTERNAL_TOKEN` | 服务间内部调用共享密钥（auth/article/search/feed 与 ai-service 保持一致） |

Python 侧（复制 `ai-service/.env.example` 为 `.env`）：

| 变量名 | 说明 |
|--------|------|
| `AI_API_KEY` | 阿里云百炼 API Key |
| `INTERNAL_TOKEN` | 与 Java 侧一致的内部令牌 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS（经 auth-service 使用） |
| `MILVUS_URI` / `REDIS_URL` / `JAVA_BASE_URL` | 中间件与 article-service 地址 |

### 3. 启动后端（按依赖顺序）

```powershell
# 构建全部模块
cd backend
mvn clean package -DskipTests

# 依次启动（顺序：无强依赖，但建议先业务后网关）
java -jar auth-service\target\auth-service-1.0.0.jar      # :8101
java -jar article-service\target\article-service-1.0.0.jar # :8102
java -jar search-service\target\search-service-1.0.0.jar   # :8103
java -jar feed-service\target\feed-service-1.0.0.jar       # :8104
java -jar gateway\target\gateway-1.0.0.jar                 # :9000 统一入口
```

### 4. 启动 ai-service

```powershell
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000   # :8000
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173（dev proxy 已指向网关 :9000）
```

---

## API 概览

所有请求经网关 `http://localhost:9000` 转发；`/api/internal/**` 不对外暴露（网关 404）。

### 认证模块（auth-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录（返回 JWT） |
| POST | /api/auth/logout | 用户登出 |

### 文章模块（article-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/articles | 发布文章（触发 MQ 索引 + AI 摘要） |
| GET | /api/articles/{id} | 获取文章详情 |
| PUT | /api/articles/{id} | 更新文章 |
| DELETE | /api/articles/{id} | 删除文章 |
| GET | /api/articles/user/{userId} | 获取用户文章列表 |

### Feed 流（feed-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/feed?mode=&cursor= | Feed 流（mode=all/following，游标分页，三级缓存） |

### 互动模块（article-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST/DELETE | /api/articles/{id}/like | 点赞 / 取消点赞 |
| POST/DELETE | /api/articles/{id}/collect | 收藏 / 取消收藏 |

### 搜索模块（search-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/search?q= | 全文搜索（function_score 加权，search_after 深分页） |
| GET | /api/search/suggest?q= | 搜索联想建议 |

### 用户模块（auth-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/users/{id} | 获取用户主页 |
| PUT | /api/users/me | 编辑个人资料 |
| POST/DELETE | /api/users/{id}/follow | 关注 / 取关 |
| GET | /api/users/{id}/following | 获取关注列表 |
| GET | /api/users/{id}/followers | 获取粉丝列表 |

### AI 模块（ai-service，SSE 流式）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/qa/ask | RAG 问答（Milvus 检索 + SSE） |
| POST | /api/ai/writing-assist | 写作助手（SSE） |
| POST | /api/ai/agent/create | 智能创作 Agent（SSE 五类事件流） |

### 文件上传（auth-service）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/oss/upload | 上传图片/文件到阿里云 OSS |

---

## 开源协议

本项目仅供学习和个人使用。

---

## 作者

- **Hazard-lzx** - [GitHub](https://github.com/Hazard-lzx)
