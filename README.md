# KnowledgeCommunity · AI 知识社区

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.0-6DB33F?style=flat&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/Vue%203-4FC08D?style=flat&logo=vuedotjs" alt="Vue 3">
  <img src="https://img.shields.io/badge/Element%20Plus-409EFF?style=flat&logo=element" alt="Element Plus">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis" alt="Redis">
  <img src="https://img.shields.io/badge/Elasticsearch-8.0-005571?style=flat&logo=elasticsearch" alt="Elasticsearch">
  <img src="https://img.shields.io/badge/RocketMQ-5.x-D77310?style=flat&logo=apacherocketmq" alt="RocketMQ">
</p>

## 项目简介

**KnowledgeCommunity** 是一个融合 AI 能力的现代化知识社区平台，支持用户发布技术文章、智能问答、个性化 Feed 流，并提供 AI 写作助手和可自定义的 AI Agent 系统。

后端采用 **Spring Boot** 微服务架构，集成 **Elasticsearch** 全文搜索、**RocketMQ** 异步消息、**Redis + Caffeine** 多级缓存、**Flyway** 数据库迁移等基础设施。前端使用 **Vue 3 + Element Plus** 构建，提供流畅的 SPA 体验。

---

## 系统架构

```
┌─────────────────────────────────────────────────┐
│            Vue 3 + Element Plus + Pinia           │
│                  (前端 SPA 界面)                   │
└──────────────────┬──────────────────────────────┘
                   │ HTTP / REST
┌──────────────────▼──────────────────────────────┐
│            Spring Boot · Java 17                 │
│               (业务后端)                          │
│                                                  │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐  │
│  │ Auth │ │Article│ │Feed  │ │Search│ │ User │  │
│  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘  │
│     │        │        │        │        │       │
│  ┌──▼────────▼────────▼────────▼────────▼──┐    │
│  │         MyBatis-Plus / MySQL              │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │  AI Q&A  │ │ Writing  │ │  Agent   │         │
│  │Assistant │ │Assistant │ │  System  │         │
│  └──────────┘ └──────────┘ └──────────┘         │
│                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │  Redis   │ │  Rocket  │ │  Elastic │         │
│  │+Caffeine │ │    MQ    │ │  search  │         │
│  └──────────┘ └──────────┘ └──────────┘         │
└─────────────────────────────────────────────────┘
```

---

## 核心功能

### 1. 用户体系 & 内容管理
- 注册 / 登录（JWT 令牌认证）
- 技术文章发布、编辑、详情浏览
- 用户个人主页、资料编辑、关注/取关
- 文章点赞互动（**Redis ZSet** 防重复 + **RocketMQ** 异步落库）

### 2. 个性化 Feed 流
- 关注用户文章聚合推送
- 无限滚动加载（Intersection Observer + 前后端双缓存）
- Redis + Caffeine 多级缓存降低数据库压力

### 3. 全文检索
- **Elasticsearch** 索引文章标题 + 内容
- 复合查询（关键词 + 分类 + 标签过滤）
- 高亮命中片段返回

### 4. AI 问答助手
- 基于 DeepSeek 大模型的文章内容问答
- 流式 SSE 响应（前端实时逐字显示）
- 双模型架构：DeepSeek-v3 聊天 + BGE 嵌入

### 5. AI 写作助手

| 功能 | 说明 |
|------|------|
| 续写 | 根据已有内容自动续写段落 |
| 大纲生成 | 根据主题自动生成文章大纲 |
| 润色 | 对选定文本进行风格优化 |
| 标签推荐 | 自动生成内容标签 |
| 智能摘要 | 提取文章核心摘要 |
| 一键发布 | 整合以上工具完成文章发布 |

### 6. AI Agent 系统
可自定义的智能体系统，每个 Agent 拥有独立：
- **身份配置**：名称、头像、角色设定
- **工具编排**：可选择启用续写、润色、摘要等工具
- **记忆服务**：对话历史持久化，支持上下文理解

---

## 技术栈

### 后端（Java）
| 技术 | 用途 |
|------|------|
| Spring Boot 3.0 | 应用框架 |
| MyBatis-Plus | ORM / MySQL 操作 |
| Spring Data Redis | 分布式缓存 |
| Spring Data Elasticsearch | 全文检索 |
| RocketMQ | 异步消息（点赞、事件通知） |
| Caffeine | 本地缓存 |
| Flyway | 数据库版本迁移 |
| JJWT | JWT 令牌生成与验证 |
| Alibaba OSS | 文件上传存储 |

### 前端
| 技术 | 用途 |
|------|------|
| Vue 3 + Vite | 前端框架 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 |
| Vue Router | 路由管理 |
| Axios | HTTP 请求 |
| SSE | 流式响应（AI 对话） |

### AI
| 技术 | 用途 |
|------|------|
| DeepSeek-v3 | 聊天 & 写作（DashScope API） |
| BAAI/bge-large-zh-v1.5 | 文本嵌入（SiliconFlow API） |
| SSE | AI 流式响应 |

### 数据库 & 中间件
| 组件 | 用途 |
|------|------|
| MySQL 8.0 | 业务数据持久化 |
| Redis 7.x | 缓存 / 点赞去重 / Session |
| Elasticsearch 8.x | 全文搜索 |
| RocketMQ 5.x | 异步消息解耦 |

---

## 项目结构

```
knowledge-community/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/knowledgecommunity/
│   │   ├── common/                   # 统一响应、业务异常、全局异常处理
│   │   ├── config/                   # Redis / ES / OSS / Security / RocketMQ / Caffeine 配置
│   │   ├── security/                 # JWT 认证过滤器 + 令牌提供者
│   │   ├── infrastructure/           # 基础设施层
│   │   │   ├── cache/                # 缓存服务抽象
│   │   │   ├── job/                  # 定时任务（计数同步、出站消息分发）
│   │   │   ├── mq/                   # RocketMQ 生产/消费
│   │   │   └── oss/                  # 文件上传服务
│   │   └── modules/                  # 业务模块
│   │       ├── auth/                 # 注册 / 登录 / JWT
│   │       ├── article/              # 文章 CRUD + ES 搜索
│   │       ├── feed/                 # 个性化 Feed 流
│   │       ├── interaction/          # 点赞互动
│   │       ├── user/                 # 用户资料 / 关注 / 事件通知
│   │       ├── search/               # 全文检索
│   │       └── ai/                   # AI 问答 + 写作助手 + Agent 系统
│   └── src/main/resources/
│       ├── application.yml           # 主配置文件
│       └── db/migration/             # Flyway 数据库迁移
│
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── views/                    # 10+ 页面组件
│   │   ├── components/               # 文章卡片 / AI 面板 / 用户头像 / 点赞 / 布局
│   │   ├── composables/              # 组合式函数（认证 / Feed / 点赞 / 滚动 / SSE）
│   │   ├── stores/                   # Pinia 状态管理
│   │   ├── api/                      # Axios 请求封装
│   │   ├── router/                   # 路由配置
│   │   └── assets/                   # 全局样式
│   └── vite.config.js
│
└── .gitignore
```

---

## 本地运行

### 前置条件
- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.x
- Elasticsearch 8.x
- RocketMQ 5.x

### 1. 启动基础设施

确保以下中间件已启动：
- **MySQL**：端口 3306，创建数据库 `knowledge_community`
- **Redis**：端口 6379
- **Elasticsearch**：端口 9200
- **RocketMQ**：端口 9876

### 2. 初始化数据库

```bash
# Flyway 会在应用启动时自动执行迁移脚本
# 脚本位于 backend/src/main/resources/db/migration/
```

### 3. 配置环境变量

在系统环境变量或 IDE 启动参数中配置：

| 变量名 | 说明 |
|--------|------|
| `DB_PASSWORD` | MySQL 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `ES_PASSWORD` | Elasticsearch 密码 |
| `AI_API_KEY` | DashScope API Key |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `SILICONFLOW_API_KEY` | SiliconFlow API Key（嵌入模型） |
| `OSS_ACCESS_KEY_ID` | 阿里云 OSS AccessKey ID |
| `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS AccessKey Secret |

### 4. 启动后端

```bash
cd backend
mvn clean install -DskipTests
mvn spring-boot:run
# 服务运行在 http://localhost:8080
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

---

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录（返回 JWT） |
| POST | /api/articles | 发布文章 |
| GET | /api/articles/{id} | 获取文章详情 |
| GET | /api/articles | 分页查询文章列表 |
| GET | /api/feed | 获取个性化 Feed 流 |
| POST | /api/interaction/like | 点赞 / 取消点赞 |
| GET | /api/search?q= | 全文搜索 |
| GET | /api/user/profile/{id} | 获取用户主页 |
| PUT | /api/user/profile | 编辑个人资料 |
| POST | /api/user/follow | 关注用户 |
| POST | /api/ai/qa | AI 问答（SSE 流式） |
| POST | /api/ai/writing/continue | AI 续写 |
| POST | /api/ai/writing/outline | AI 大纲生成 |
| POST | /api/ai/writing/polish | AI 文本润色 |
| POST | /api/ai/writing/summary | AI 摘要提取 |
| POST | /api/ai/writing/tags | AI 标签推荐 |
| POST | /api/ai/agents | 创建 AI Agent |
| GET | /api/ai/agents | 获取 Agent 列表 |
| POST | /api/ai/agents/{id}/chat | Agent 对话 |

---

## 开源协议

本项目仅供学习和个人使用。

---

## 作者

- **Hazard-lzx** - [GitHub](https://github.com/Hazard-lzx)
