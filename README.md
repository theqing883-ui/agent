# JChatMind

<div align="center">

**一个基于 Spring AI 的智能体（Agent）平台**

支持多模型、多工具、知识库（RAG）和 MCP 协议扩展的 AI 对话系统

</div>

---

## ✨ 核心特性

- **🤖 多智能体管理** — 创建、编辑、删除智能体，自定义系统提示词、模型、工具和知识库
- **💬 实时流式对话** — 基于 SSE（Server-Sent Events）的实时消息推送，支持 AI 思考过程可视化
- **🔧 可扩展工具系统** — 内置固定工具（知识库检索、任务终止）和可选工具（数据库查询、联网搜索），支持自定义扩展
- **📚 知识库（RAG）** — Markdown 文档上传 → BGE-M3 向量嵌入 → PostgreSQL pgvector 语义检索
- **🧠 上下文窗口管理** — Token 感知记忆、智能截断、记忆笔记、对话摘要，高效管理长对话
- **🔗 MCP 协议集成** — 支持 Model Context Protocol，可连接外部 MCP Server 扩展工具能力
- **🔄 Agent 自主循环** — Think → Execute 循环，最多 20 步自主推理与工具调用
- **🎨 现代前端界面** — React 19 + Ant Design 6 + Tailwind CSS，简洁美观的用户体验

---

## 🏗️ 技术栈

| 层级 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5.8 |
| **AI 框架** | Spring AI 1.1.0 |
| **AI 模型** | DeepSeek (`deepseek-chat`) / 智谱 AI (`glm-4.6`) |
| **数据库** | PostgreSQL + Redis |
| **ORM** | MyBatis |
| **向量检索** | BGE-M3 嵌入 + pgvector |
| **实时通信** | SSE (Server-Sent Events) |
| **前端框架** | React 19 + TypeScript |
| **UI 组件库** | Ant Design 6 + `@ant-design/x` |
| **样式** | Tailwind CSS 4 |
| **构建工具** | Vite (rolldown-vite) |
| **包管理** | Maven (后端) + npm (前端) |
| **语言** | Java 17 |

---

## 📁 项目结构

```
JChatMind/
├── jchatmind01/                  # 后端 Spring Boot 项目
│   ├── src/main/java/com/kaer/
│   │   ├── agent/                # Agent 核心（JChatMind 主循环、工厂、状态机）
│   │   │   └── tools/            # 工具定义（Knowledge、DataBase、BochaSearch、Terminate 等）
│   │   ├── config/               # 配置（ChatClient 注册、CORS、异步）
│   │   ├── context/              # 上下文管理（Token 记忆、窗口管理、截断器）
│   │   ├── controller/           # REST 控制器（Agent、ChatSession、ChatMessage、KnowledgeBase、Document、SSE）
│   │   ├── converter/            # 对象转换器
│   │   ├── event/                # 事件驱动（ChatEvent → ChatEventListener）
│   │   ├── mapper/               # MyBatis Mapper
│   │   ├── message/              # SSE 消息体定义
│   │   ├── model/                # 数据模型（entity、dto、vo、request、response）
│   │   └── service/              # 业务服务层
│   ├── src/main/resources/
│   │   ├── application.yml       # 应用配置
│   │   └── mapper/               # MyBatis XML 映射文件
│   ├── mcp/                      # MCP Server（Bocha 搜索）
│   │   └── bocha-mcp.js          # 基于 MCP SDK 的博查搜索服务
│   └── data/                     # 数据目录（文档存储、输出文件）
│
├── ui/                           # 前端 React 项目
│   ├── src/
│   │   ├── api/                  # API 请求层
│   │   ├── components/           # 组件
│   │   │   ├── views/            # 视图（AgentChatView、KnowledgeBaseView）
│   │   │   ├── tabs/             # 侧边栏 Tab 内容
│   │   │   └── modals/           # 模态框
│   │   ├── contexts/             # React Context
│   │   ├── hooks/                # 自定义 Hooks
│   │   ├── layout/               # 布局组件
│   │   └── types/                # TypeScript 类型定义
│   └── package.json
│
├── JChatMind.iml                 # IntelliJ IDEA 模块文件
└── README.md
```

---

## 🚀 快速开始

### 环境要求

- **JDK 17+**
- **Maven 3.8+**
- **Node.js 18+**
- **PostgreSQL**（需安装 pgvector 扩展）
- **Redis**

### 1. 克隆项目

```bash
git clone https://github.com/yourusername/JChatMind.git
cd JChatMind
```

### 2. 配置数据库

创建 PostgreSQL 数据库并启用 pgvector 扩展：

```sql
CREATE DATABASE jchatmind;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置环境变量

设置 API Key 等环境变量：

```bash
# DeepSeek API Key (必须)
export API-KEY-deepseek=sk-your-deepseek-api-key

# 智谱 AI API Key (可选)
export API-KEY-zhipuai=your-zhipuai-api-key

# 博查搜索 API Key (可选，用于联网搜索)
export API-KEY-bocha-search=your-bocha-api-key

# QQ 邮箱 SMTP 授权码 (可选，用于邮件发送)
export QQ-SMTP=your-qq-smtp-code
```

### 4. 启动后端

```bash
cd jchatmind01
mvn spring-boot:run
```

后端服务默认运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd ui
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`，会自动代理 API 请求到后端。

---

## 🔄 工作流程

```
用户发送消息
    │
    ▼
ChatMessageController (REST API)
    │
    ▼
ChatEvent 事件发布
    │
    ▼
ChatEventListener (异步处理)
    │
    ▼
JChatMindFactory.create()  ──  加载 Agent 配置、聊天历史、工具、知识库
    │
    ▼
JChatMind.run()  ──  Agent 主循环 (最多 20 步)
    │
    ├── think()  ──  调用 AI 模型，决定是否使用工具
    │       │
    │       ├── 无工具调用 → FINISHED
    │       └── 有工具调用 → execute()
    │               │
    │               └── 执行工具 → 结果反馈给 AI → 继续 think()
    │
    └── SSE 实时推送 ──  思考过程 / 工具调用 / 生成内容 → 前端展示
```

### Agent 状态机

```
IDLE → PLANNING → THINKING → EXECUTING → FINISHED / ERROR
```

### SSE 消息类型

| 类型 | 说明 |
|------|------|
| `AI_PLANNING` | AI 正在规划 |
| `AI_THINKING` | AI 正在思考 |
| `AI_EXECUTING` | AI 正在执行工具 |
| `AI_GENERATED_CONTENT` | AI 生成的内容 |
| `AI_DONE` | AI 任务完成 |

---

## 🛠️ 内置工具

### 固定工具（所有 Agent 默认启用）

| 工具 | 说明 |
|------|------|
| `KnowledgeTool` | 从知识库执行语义检索（RAG），基于 BGE-M3 向量相似度搜索 |
| `TerminateTool` | 任务完成后，Agent 调用此工具跳出循环 |

### 可选工具（按需配置）

| 工具 | 说明 |
|------|------|
| `DataBaseTools` | PostgreSQL 只读查询（SELECT），结果以 Markdown 表格返回 |
| `BochaSearchTool` | 通过 MCP 协议调用博查 API 进行实时联网搜索 |
| 本地文件系统 | 通过 MCP Filesystem Server 读写本地文件 |

---

## 📡 API 概览

### Agent 管理
- `POST   /api/agents` — 创建智能体
- `GET    /api/agents` — 获取所有智能体
- `PATCH  /api/agents/{id}` — 更新智能体
- `DELETE /api/agents/{id}` — 删除智能体

### 聊天会话
- `POST   /api/chat-sessions` — 创建聊天会话
- `GET    /api/chat-sessions` — 获取所有会话
- `GET    /api/chat-sessions/{id}` — 获取单个会话
- `GET    /api/chat-sessions/agent/{agentId}` — 按 Agent 获取会话
- `PATCH  /api/chat-sessions/{id}` — 更新会话
- `DELETE /api/chat-sessions/{id}` — 删除会话

### 聊天消息
- `POST   /api/chat-messages` — 发送消息
- `GET    /api/chat-messages/session/{sessionId}` — 获取会话消息

### 知识库 & 文档
- `POST   /api/knowledge-bases` — 创建知识库
- `GET    /api/knowledge-bases` — 获取所有知识库
- `POST   /api/documents/upload` — 上传 Markdown 文档
- `GET    /api/documents/kb/{kbId}` — 获取知识库文档列表

### 实时连接
- `GET    /sse/connect/{chatSessionId}` — SSE 实时消息流

---

## 🎯 使用指南

1. **创建智能体** — 点击左侧「智能体助手」Tab，新建 Agent，配置名称、系统提示词、模型和工具
2. **开始对话** — 选择 Agent 后在输入框发送消息，观察 AI 的思考过程和工具调用
3. **管理知识库** — 在「知识库」Tab 创建知识库，上传 Markdown 文档，Agent 将在对话中自动检索
4. **查看聊天记录** — 左侧「聊天记录」Tab 可查看、切换历史对话

---

<div align="center">

**Built with ❤️ using Spring AI & React**

</div>
