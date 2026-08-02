# KnowledgeCommunity · AI 知识社区

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F?style=flat&logo=spring" alt="Spring AI">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/Vue%203-4FC08D?style=flat&logo=vuedotjs" alt="Vue 3">
  <img src="https://img.shields.io/badge/Element%20Plus-409EFF?style=flat&logo=element" alt="Element Plus">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis" alt="Redis">
  <img src="https://img.shields.io/badge/Elasticsearch-8.0-005571?style=flat&logo=elasticsearch" alt="Elasticsearch">
  <img src="https://img.shields.io/badge/RocketMQ-5.x-D77310?style=flat&logo=apacherocketmq" alt="RocketMQ">
  <img src="https://img.shields.io/badge/MyBatis--Plus-3.5-8B2252?style=flat&logo=mybatis" alt="MyBatis-Plus">
  <img src="https://img.shields.io/badge/Alibaba%20OSS-FF6A00?style=flat&logo=alibabacloud" alt="Alibaba OSS">
  <img src="https://img.shields.io/badge/Vite-5.2-646CFF?style=flat&logo=vite" alt="Vite">
</p>

## 项目简介

**KnowledgeCommunity** 是一个融合 AI 能力的现代化知识社区平台，支持用户发布技术文章、智能问答、个性化 Feed 流，并提供 AI 写作助手和可自定义的 AI Agent 智能创作系统。

后端采用 **Spring Boot 3.2.5** 架构，集成 **Elasticsearch** 全文搜索、**RocketMQ** 异步消息、**Redis + Caffeine** 多级缓存、**Spring AI 1.0.0** 对接阿里云百炼大模型。前端使用 **Vue 3 + Element Plus + Vite** 构建，提供流畅的 SPA 体验。

---

## 系统架构

```
┌─────────────────────────────────────────────────┐
│            Vue 3 + Element Plus + Pinia           │
│          (Vite 5 构建 · SPA 前端界面)              │
└──────────────────┬──────────────────────────────┘
                   │ HTTP / REST / SSE
┌──────────────────▼──────────────────────────────┐
│            Spring Boot 3.2.5 · Java 17           │
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
│  │  AI RAG  │ │ Writing  │ │  Agent   │         │
│  │  Q&A     │ │Assistant │ │  System  │         │
│  └──────────┘ └──────────┘ └──────────┘         │
│                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │  Redis   │ │  Rocket  │ │  Elastic │         │
│  │+Caffeine │ │    MQ    │ │  search  │         │
│  └──────────┘ └──────────┘ └──────────┘         │
│                                                  │
│  ┌──────────────────────────────────────┐        │
│  │   Spring AI 1.0.0 · 阿里云百炼        │        │
│  │   deepseek-v3 · text-embedding-v2    │        │
│  └──────────────────────────────────────┘        │
└─────────────────────────────────────────────────┘
```

---

## 核心功能

### 1. 用户体系 & 内容管理
- 注册 / 登录 / 登出（JWT 无状态认证，BCrypt 密码加密）
- 技术文章发布、编辑、删除、详情浏览
- 用户个人主页、资料编辑、关注/取关、关注列表/粉丝列表
- 文章点赞互动（Redis 去重 + RocketMQ 异步落库）
- 文章收藏功能

### 2. 个性化 Feed 流
- **全站模式** / **关注模式** 双模式切换
- 关注用户文章聚合推送
- 无限滚动加载（游标分页 + Redis + Caffeine 多级缓存）
- Intersection Observer 自动触发加载

### 3. 全文检索
- **Elasticsearch** 索引文章标题 + 内容
- 关键词高亮命中片段返回
- 搜索联想建议（suggest API）

### 4. AI RAG 问答
- 基于文章内容的 RAG 智能问答
- 文章分块 → Embedding 向量化 → Redis 存储 → 余弦相似度检索
- **SSE 流式响应**，前端实时逐字显示

### 5. AI 写作助手

| 功能 | 说明 |
|------|------|
| 续写 | 根据已有内容自动续写段落 |
| 大纲生成 | 根据主题自动生成文章大纲 |
| 润色 | 对选定文本进行风格优化 |
| 标签推荐 | 自动生成内容标签 |
| 智能摘要 | 提取文章核心摘要 |

### 6. AI Agent 智能创作
可自定义的智能体系统，每个 Agent 拥有独立：
- **身份配置**：名称、头像、角色设定
- **工具编排**：可选择启用续写、润色、摘要、大纲、标签推荐、一键发布等工具
- **ReAct 循环**：基于 Spring AI ToolCallingManager 的多轮迭代推理
- **记忆服务**：对话历史持久化，支持上下文理解

---

## 技术栈

### 后端（Java）
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Security | 6.x | 认证与授权 |
| Spring AI | 1.0.0 | AI 模型集成（OpenAI-compatible 模式） |
| MyBatis-Plus | 3.5.6 | ORM / MySQL 操作 |
| Spring Data Redis | - | 分布式缓存 |
| Spring Data Elasticsearch | - | 全文检索 |
| RocketMQ | 2.3.0 | 异步消息（点赞、关注事件） |
| Caffeine | - | 本地缓存 |
| JJWT | 0.12.5 | JWT 令牌生成与验证 |
| Alibaba OSS | 3.17.4 | 文件上传存储 |

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

### AI
| 技术 | 用途 |
|------|------|
| deepseek-v3 | 聊天 & 写作（阿里云百炼 DashScope） |
| text-embedding-v2 | 文本嵌入（阿里云百炼 DashScope） |
| Spring AI 1.0.0 | ChatClient 流式对话 + ToolCallingManager Agent |
| SSE | AI 流式响应 |

### 数据库 & 中间件
| 组件 | 用途 |
|------|------|
| MySQL 8.0 | 业务数据持久化 |
| Redis 7.x | 缓存 / Embedding 向量存储 / 点赞去重 |
| Elasticsearch 8.x | 全文搜索 |
| RocketMQ 5.x | 异步消息解耦 |

---

## 项目结构

```
knowledge-community/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/knowledgecommunity/
│   │   ├── common/                   # 统一响应、业务异常、全局异常处理
│   │   ├── config/                   # Security / Redis / ES / OSS / RocketMQ / Caffeine / WebMVC 配置
│   │   ├── security/                 # JWT 认证过滤器 + 令牌提供者 + 用户主体
│   │   ├── infrastructure/           # 基础设施层
│   │   │   ├── cache/                # 缓存服务抽象
│   │   │   ├── job/                  # 定时任务（计数校验、计数同步、发件箱投递）
│   │   │   ├── mq/                   # RocketMQ 消费者（文章发布、用户关系事件）+ 生产者
│   │   │   └── oss/                  # 阿里云 OSS 文件上传
│   │   └── modules/                  # 业务模块
│   │       ├── auth/                 # 注册 / 登录 / 登出
│   │       ├── article/              # 文章 CRUD + ES 文档索引
│   │       ├── feed/                 # 个性化 Feed 流（全站/关注模式，游标分页）
│   │       ├── interaction/          # 点赞 / 收藏
│   │       ├── user/                 # 用户资料 / 关注 / 粉丝 / 事件发件箱
│   │       ├── search/               # ES 全文检索 + 搜索联想
│   │       └── ai/                   # AI 模块
│   │           ├── config/           # EmbeddingModel 手动配置
│   │           ├── controller/       # RAG 问答 / 写作助手 / Agent 智能创作
│   │           ├── service/          # AiService / WritingAssistant / AgentService / AgentMemory
│   │           ├── dto/              # 请求/响应 DTO
│   │           └── tools/            # Agent 工具（续写/润色/大纲/摘要/标签/发布）
│   └── src/main/resources/
│       ├── application.yml           # 主配置（环境变量注入）
│       └── application-dev.yml       # 开发环境配置（已 gitignore）
│
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── views/                    # 页面组件（登录/注册/Feed/文章/搜索/问答/发布/个人主页/Agent/学习）
│   │   ├── components/               # 公共组件
│   │   │   ├── layout/               # AppLayout / NavSidebar
│   │   │   ├── article/              # ArticleCard / ArticleEditor
│   │   │   ├── ai/                   # AiAssistant / ArticleQaPanel / ContinueTab / OutlineTab / PolishTab
│   │   │   ├── user/                 # UserStats
│   │   │   └── common/               # LikeButton / SkeletonCard / StreamText / UserAvatar
│   │   ├── composables/              # 组合式函数（认证/Feed/无限滚动/点赞/SSE）
│   │   ├── stores/                   # Pinia 状态管理（auth / feed）
│   │   ├── api/                      # Axios 请求封装
│   │   ├── router/                   # 路由配置（含导航守卫）
│   │   └── assets/styles/            # SCSS 全局样式
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

### 2. 配置环境变量

在系统环境变量或 IDE 启动参数中配置：

| 变量名 | 说明 |
|--------|------|
| `DB_PASSWORD` | MySQL 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `ES_PASSWORD` | Elasticsearch 密码 |
| `AI_API_KEY` | 阿里云百炼 API Key |
| `OSS_ACCESS_KEY_ID` | 阿里云 OSS AccessKey ID |
| `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS AccessKey Secret |

### 3. 启动后端

```bash
cd backend
mvn clean install -DskipTests
mvn spring-boot:run
# 服务运行在 http://localhost:8080
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

---

## API 概览

### 认证模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录（返回 JWT） |
| POST | /api/auth/logout | 用户登出 |

### 文章模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/articles | 发布文章 |
| GET | /api/articles/{id} | 获取文章详情 |
| PUT | /api/articles/{id} | 更新文章 |
| DELETE | /api/articles/{id} | 删除文章 |
| GET | /api/articles/user/{userId} | 获取用户文章列表 |

### Feed 流
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/feed?mode=&cursor= | 获取 Feed 流（mode=all/following，游标分页） |

### 互动模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST/DELETE | /api/articles/{id}/like | 点赞 / 取消点赞 |
| POST/DELETE | /api/articles/{id}/collect | 收藏 / 取消收藏 |

### 搜索模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/search?q= | 全文搜索 |
| GET | /api/search/suggest?q= | 搜索联想建议 |

### 用户模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/users/{id} | 获取用户主页 |
| PUT | /api/users/me | 编辑个人资料 |
| POST/DELETE | /api/users/{id}/follow | 关注 / 取关 |
| GET | /api/users/{id}/following | 获取关注列表 |
| GET | /api/users/{id}/followers | 获取粉丝列表 |

### AI 模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/qa/ask | RAG 问答（SSE 流式） |
| POST | /api/ai/writing-assist | 写作助手（SSE 流式） |
| POST | /api/ai/agent/create | 智能创作 Agent（SSE 流式） |

### 文件上传
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/oss/upload | 上传图片/文件到阿里云 OSS |

---

## 开源协议

本项目仅供学习和个人使用。

---

## 作者

- **Hazard-lzx** - [GitHub](https://github.com/Hazard-lzx)