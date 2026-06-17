<div align="center">

# 🤖 JChatMind

**基于 Spring AI 的多智能体协作平台**

支持多模型 · 工具扩展 · 知识库（RAG）· 任务委派 · MCP 协议 · 实时流式对话

</div>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring_Boot-3.5.8-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.5.8">
  <img src="https://img.shields.io/badge/Spring_AI-1.1.0-6DB33F?logo=spring&logoColor=white" alt="Spring AI 1.1.0">
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black" alt="React 19">
  <img src="https://img.shields.io/badge/Ant_Design-6-0170FE?logo=antdesign&logoColor=white" alt="Ant Design 6">
  <img src="https://img.shields.io/badge/Tailwind_CSS-4-06B6D4?logo=tailwindcss&logoColor=white" alt="Tailwind CSS 4">
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL + pgvector">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License">
</p>

---

## 📖 目录

- [核心特性](#-核心特性)
- [技术栈](#-技术栈)
- [项目结构](#-项目结构)
- [架构设计](#-架构设计)
- [快速开始](#-快速开始)
- [Agent 工作流](#-agent-工作流)
- [内置工具](#-内置工具)
- [多 Agent 任务委派](#-多-agent-任务委派)
- [技能系统](#-技能系统)
- [知识库 & RAG](#-知识库--rag)
- [上下文窗口管理](#-上下文窗口管理)
- [API 概览](#-api-概览)
- [前端界面](#-前端界面)
- [RAG 评测](#-rag-评测)

---

## ✨ 核心特性

### 🤖 多智能体管理
创建、编辑、删除智能体（Agent），自定义系统提示词、底层模型、启用的工具以及关联的知识库。每个 Agent 拥有独立的对话上下文和记忆。

### 💬 实时流式对话
基于 **SSE（Server-Sent Events）** 实现实时消息推送。AI 的思考过程、工具调用和生成内容逐条推送到前端，支持流式 Markdown 渲染（`@ant-design/x-markdown`）。

### 🧠 Agent 自主推理循环
**Think → Execute** 主循环，Agent 自主决定何时调用工具、何时结束对话。最多 20 步推理，内置状态机（`IDLE → PLANNING → THINKING → EXECUTING → FINISHED / ERROR`）。

### 🔧 可扩展工具系统
工具分为 **FIXED（固定）** 和 **OPTIONAL（可选）** 两种类型。FIXED 工具所有 Agent 默认拥有；OPTIONAL 工具可按需为每个 Agent 单独配置。支持通过 MCP 协议接入外部工具。

### 🔗 多 Agent 任务委派
主 Agent 可将独立子任务委派给子 Agent 执行。子 Agent 在隔离的上下文环境中专注工作，完成后返回精简结论给主 Agent。内置防递归委派机制。

### 🎯 技能系统
基于 Markdown 文件的插件式技能系统。Agent 运行时可通过 `loadSkill` 工具按需加载专项能力（如深度研究、代码审查等），技能指令注入对话上下文。

### 📚 知识库（RAG）
- Markdown 文档上传 → `flexmark` 解析 → **BGE-M3** 向量嵌入 → PostgreSQL **pgvector** 混合检索
- 支持语义搜索 + 关键词搜索的**混合检索**策略
- 上下文精确度 0.71、上下文召回率 0.93（RAG 评测结果）

### 🧠 上下文窗口管理
Token 感知的聊天记忆，支持智能截断、记忆笔记自动生成、对话摘要。通过 `ContextWindowManager` 管理输入 Token 预算，确保不超出模型上下文限制。

### 🔗 MCP 协议集成
支持 **Model Context Protocol**，可连接外部 MCP Server（如 Bocha 搜索、Filesystem Server）扩展工具能力。内置 `McpToolManager` 统一管理 MCP 工具生命周期。

---

## 🏗️ 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **后端框架** | Spring Boot 3.5.8 | 应用核心框架 |
| **AI 框架** | Spring AI 1.1.0 | AI 模型调用、工具管理、MCP 集成 |
| **AI 模型** | DeepSeek (`deepseek-chat`) / 智谱 AI (`glm-4.6`) | 双模型支持，可切换 |
| **向量嵌入** | BGE-M3 | 文档语义向量化 |
| **数据库** | PostgreSQL + pgvector | 业务数据 + 向量检索 |
| **缓存** | Redis + Jedis | 会话缓存、SSE 连接管理 |
| **ORM** | MyBatis 3.0.3 | 数据访问层 |
| **Markdown 解析** | flexmark 0.64.8 | 文档解析与分块 |
| **实时通信** | SSE (Server-Sent Events) | 服务端实时消息推送 |
| **前端框架** | React 19 + TypeScript 5.9 | 现代化 UI 开发 |
| **UI 组件库** | Ant Design 6 + `@ant-design/x` 2.0 | AI 对话专用组件 |
| **样式** | Tailwind CSS 4 | 原子化 CSS |
| **构建工具** | Vite (rolldown-vite 7.2.5) | 前端构建 |
| **包管理** | Maven + npm | 依赖管理 |
| **语言** | Java 17 | 后端开发语言 |

---

## 📁 项目结构

```
JChatMind/
├── jchatmind01/                          # 后端 Spring Boot 项目
│   ├── src/main/java/com/kaer/
│   │   ├── agent/                        # Agent 核心引擎
│   │   │   ├── JChatMind.java            #   主循环（Think → Execute）
│   │   │   ├── JChatMindFactory.java     #   Agent 工厂（含子 Agent 创建）
│   │   │   ├── AgentState.java           #   状态机枚举
│   │   │   ├── AgentContextHolder.java   #   ThreadLocal 上下文
│   │   │   ├── AgentRoleConstants.java   #   角色常量（PARENT/CHILD）
│   │   │   ├── DelegationConfig.java     #   委派配置
│   │   │   ├── SystemPrompt.java         #   系统提示词模板
│   │   │   ├── skill/                    #   技能管理器
│   │   │   │   ├── SkillManager.java     #     启动扫描、注册表
│   │   │   │   └── SkillMeta.java        #     技能元数据
│   │   │   └── tools/                    #   工具实现
│   │   │       ├── KnowledgeTools.java   #     RAG 知识库检索
│   │   │       ├── DataBaseTools.java    #     PostgreSQL 只读查询
│   │   │       ├── DelegationTool.java   #     多 Agent 任务委派
│   │   │       ├── SkillTool.java        #     按需加载技能
│   │   │       ├── TerminateTool.java    #     任务终止
│   │   │       ├── DirectAnswerTool.java #     直接回答（绕过循环）
│   │   │       ├── EmailTools.java       #     邮件发送
│   │   │       ├── FileSystemTools.java  #     本地文件读写
│   │   │       ├── McpToolManager.java   #     MCP 工具生命周期管理
│   │   │       ├── Tool.java             #     工具接口
│   │   │       └── ToolType.java         #     工具类型（FIXED/OPTIONAL）
│   │   ├── config/                       # 配置类
│   │   │   ├── ChatClientRegistry.java   #   ChatClient 注册
│   │   │   ├── MultiChatClientConfig.java#   多模型客户端配置
│   │   │   ├── CorsConfig.java           #   跨域配置
│   │   │   ├── AsyncConfig.java          #   异步配置
│   │   │   └── RedisConfig.java          #   Redis 配置
│   │   ├── context/                      # 上下文窗口工程
│   │   │   ├── model/                    #   ContextWindow、TokenUsage、BudgetAllocation
│   │   │   ├── manager/                  #   ContextWindowManager
│   │   │   ├── memory/                   #   TokenAwareChatMemory
│   │   │   ├── token/                    #   TokenCounter
│   │   │   ├── budget/                   #   BudgetAllocator
│   │   │   └── truncator/                #   ContextTruncator（工具响应截断）
│   │   ├── controller/                   # REST 控制器
│   │   │   ├── AgentController.java      #   智能体 CRUD
│   │   │   ├── ChatSessionController.java#   聊天会话管理
│   │   │   ├── ChatMessageController.java#   消息收发
│   │   │   ├── SseController.java        #   SSE 连接
│   │   │   ├── KnowledgeBaseController.java# 知识库管理
│   │   │   ├── DocumentController.java   #   文档上传
│   │   │   └── ToolController.java       #   工具信息
│   │   ├── service/                      # 业务服务层
│   │   ├── model/                        # 数据模型（entity/dto/vo/request/response）
│   │   ├── mapper/                       # MyBatis Mapper 接口
│   │   ├── converter/                    # 对象转换器（Entity ↔ DTO ↔ VO）
│   │   ├── event/                        # 事件驱动（ChatEvent → ChatEventListener）
│   │   ├── message/                      # SSE 消息体（SseMessage）
│   │   ├── typehandler/                  # MyBatis 类型处理器（pgvector）
│   │   └── exception/                    # 自定义异常
│   ├── src/main/resources/
│   │   ├── application.yml               #   应用配置
│   │   ├── mapper/                       #   MyBatis XML 映射文件
│   │   └── skills/                       #   技能 Markdown 文件
│   ├── mcp/                              # MCP Server 定义
│   │   └── bocha-mcp.js                  #   Bocha 搜索 MCP Server
│   └── data/                             # 运行时数据（文档存储、输出文件）
│
├── ui/                                   # 前端 React 项目
│   ├── src/
│   │   ├── api/                          #   API 请求层（axios 封装）
│   │   ├── components/
│   │   │   ├── views/                    #   主视图（AgentChatView、KnowledgeBaseView）
│   │   │   ├── tabs/                     #   侧边栏 Tab（Agent、Chat、KnowledgeBase）
│   │   │   └── modals/                   #   模态框（AddAgent、AddKnowledgeBase）
│   │   ├── contexts/                     #   React Context（ChatSessions）
│   │   ├── hooks/                        #   自定义 Hooks（useAgents、useChatSessions、…）
│   │   ├── layout/                       #   布局组件（Layout、Sidebar、Content）
│   │   ├── types/                        #   TypeScript 类型定义
│   │   └── utils/                        #   工具函数
│   └── package.json
│
├── rag测试脚本/                           # RAG 评测工具
│   ├── rag_evaluation_tester.py          #   RAG 评测脚本（Ragas 框架）
│   ├── test_dataset.json                 #   测试数据集
│   └── 测试文本/                          #   测试用文档
│
└── README.md
```

---

## 🏛️ 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (React 19)                    │
│  Ant Design 6 · @ant-design/x · Tailwind CSS 4 · SSE Client  │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP REST + SSE Stream
┌──────────────────────────▼──────────────────────────────────┐
│                   Spring Boot Controllers                    │
│  Agent · ChatSession · ChatMessage · KnowledgeBase · SSE    │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    Service / Facade Layer                     │
│  AgentFacadeService · ChatSessionFacadeService               │
│  ChatMessageFacadeService · KnowledgeBaseFacadeService       │
│  SseService · EmailService · MarkdownParserService           │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                     Agent Core Engine                         │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ JChatMind│  │  Delegation  │  │  Context Window      │   │
│  │  Loop    │  │  Tool        │  │  Manager             │   │
│  │Think→Exec│  │  Parent→Child│  │  TokenAwareChatMemory│   │
│  └──────────┘  └──────────────┘  └──────────────────────┘   │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  Skill   │  │  Tool System │  │  MCP Tool Manager     │   │
│  │  Manager │  │  FIXED/OPT   │  │  External Servers     │   │
│  └──────────┘  └──────────────┘  └──────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    Spring AI 1.1.0                            │
│  DeepSeek Chat · 智谱 AI GLM · BGE-M3 Embedding              │
├──────────────────────────────────────────────────────────────┤
│                Data Layer                                     │
│  PostgreSQL (pgvector) · Redis · MyBatis                     │
└──────────────────────────────────────────────────────────────┘
```

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| PostgreSQL | 14+（需 pgvector 扩展） |
| Redis | 6+ |

### 1. 克隆项目

```bash
git clone https://github.com/yourusername/JChatMind.git
cd JChatMind
```

### 2. 配置数据库

```sql
CREATE DATABASE jchatmind;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### 3. 配置环境变量

```bash
# DeepSeek API Key（必须）
export API-KEY-deepseek=sk-your-deepseek-api-key

# 智谱 AI API Key（可选）
export API-KEY-zhipuai=your-zhipuai-api-key

# Bocha 搜索 API Key（可选，联网搜索工具）
export API-KEY-bocha-search=your-bocha-api-key

# QQ 邮箱 SMTP 授权码（可选，邮件发送工具）
export QQ-SMTP=your-qq-smtp-code
```

### 4. 启动后端

```bash
cd jchatmind01
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd ui
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，API 请求自动代理到后端。

---

## 🔄 Agent 工作流

### 主循环

```
用户发送消息
    │
    ▼
ChatMessageController (REST API)  — 持久化消息
    │
    ▼
ChatEvent 发布
    │
    ▼
ChatEventListener (异步)
    │
    ▼
JChatMindFactory.create()  — 加载 Agent 配置、对话历史、工具、知识库、技能列表
    │
    ▼
┌─────────────────────────────────────────────┐
│  JChatMind.run()  —  Agent 主循环（最多 20 步） │
│                                              │
│   ┌──────────┐     ┌──────────┐              │
│   │ think()  │────▶│execute() │              │
│   │ 调用 AI  │ 是   │ 执行工具  │              │
│   │ 决策工具  │◀────│ 反馈结果  │              │
│   └────┬─────┘     └──────────┘              │
│        │ 否                                   │
│        ▼                                      │
│   FINISHED  —  对话结束                        │
└─────────────────────────────────────────────┘
    │
    ▼
SSE 实时推送  —  思考过程 / 工具调用 / 生成内容 → 前端展示
```

### Agent 状态机

```
IDLE ──▶ PLANNING ──▶ THINKING ──▶ EXECUTING ──▶ FINISHED
  ▲                                                  │
  └──────────────── ERROR ◀──────────────────────────┘
```

### SSE 消息类型

| 类型 | 说明 |
|------|------|
| `AI_PLANNING` | AI 正在规划任务 |
| `AI_THINKING` | AI 正在思考 |
| `AI_EXECUTING` | AI 正在执行工具 |
| `AI_GENERATED_CONTENT` | AI 生成的内容片段 |
| `AI_DONE` | AI 任务完成 |

---

## 🛠️ 内置工具

### FIXED 工具（所有 Agent 默认启用）

| 工具 | 说明 |
|------|------|
| `KnowledgeTools` | 从知识库执行语义检索（RAG），BGE-M3 向量相似度 + 关键词混合搜索 |
| `TerminateTool` | Agent 完成任务后调用，跳出主循环 |
| `DelegationTool` | 将子任务委派给独立的子 Agent 执行 |
| `SkillTool` | 按需加载技能指令到对话上下文 |
| `DirectAnswerTool` | 直接回答用户问题，绕过 Think→Execute 循环 |

### OPTIONAL 工具（按需配置）

| 工具 | 说明 |
|------|------|
| `DataBaseTools` | PostgreSQL 只读查询（SELECT），结果以 Markdown 表格返回 |
| `EmailTools` | 通过 QQ SMTP 发送邮件 |
| `FileSystemTools` | 通过 MCP Filesystem Server 读写本地文件 |
| MCP 外部工具 | 通过 `McpToolManager` 接入任意 MCP Server 的工具 |

---

## 🔗 多 Agent 任务委派

当任务复杂度较高时，主 Agent 可通过 `DelegationTool` 将独立子任务委派给子 Agent 执行。

### 执行流程

```
主 Agent think()
    │
    ├── 决定委派 → 调用 delegateTask(taskDescription, childAgentId?)
    │
    ▼
DelegationTool.delegateTask()
    │
    ├── ① 创建子会话（关联 parentSessionId）
    ├── ② 持久化任务指令为子会话的 USER 消息
    ├── ③ 通过 JChatMindFactory 创建子 Agent
    │       · 隔离的上下文窗口
    │       · 裁剪工具列表（排除 delegateTask，防递归）
    │       · 缩减步数（默认 10 步）
    │       · 独立的子会话 ID
    ├── ④ 同步运行子 Agent 直至完成
    ├── ⑤ 提取子 Agent 最终结论（丢弃中间执行过程）
    └── ⑥ 返回结论给主 Agent → 主 Agent 继续 think()
```

### 关键设计

- **防递归**：子 Agent 自动排除 `delegateTask` 工具
- **上下文隔离**：子 Agent 拥有独立会话和记忆，互不干扰
- **结果精简**：仅提取最终结论，丢弃 tools calls / tool responses 等中间噪声
- **可配置**：子 Agent 最大步数、排除工具列表、结果截断长度均可通过 `application.yml` 配置

```yaml
# application.yml
agent:
  delegation:
    child-max-steps: 10          # 子 Agent 最大循环步数
    excluded-tools:              # 子 Agent 排除的工具
      - delegateTask
      - loadSkill
    max-result-length: 4000      # 返回结论最大字符数
```

---

## 🎯 技能系统

技能是以 Markdown 文件形式存放在 `classpath:skills/` 下的可插拔能力模块。每个技能包含 YAML frontmatter 元数据和 Markdown 正文指令。

### 技能文件格式

```markdown
---
name: deep-research
description: 深度研究——多轮搜索、交叉验证、生成引用报告
---

# 深度研究技能

## 执行流程
1. 分解研究问题为多个子问题
2. 对每个子问题执行联网搜索
...
```

### 工作流程

```
启动时
  SkillManager.init()
    → 扫描 classpath:skills/*.md
    → 解析 frontmatter（name, description）
    → 注册到技能注册表

运行时
  System prompt 中包含可用技能列表
    → Agent 识别需要专项能力
    → 调用 loadSkill(skillName)
    → Skill 正文注入为 SystemMessage
    → 后续 think() 循环自动应用技能指令
```

### 特性

- **按需加载**：运行时通过工具调用注入，不占用初始上下文
- **幂等防护**：同一技能在同一会话不重复加载
- **热注册**：添加 `.md` 文件并重启即可生效

---

## 📚 知识库 & RAG

### 文档处理流水线

```
Markdown 文档上传
    │
    ▼
flexmark 解析器 → 提取纯文本
    │
    ▼
文本分块（可配置大小）
    │
    ▼
BGE-M3 嵌入模型 → 1024 维向量
    │
    ▼
PostgreSQL pgvector 存储
    │
    ▼
Agent 调用 KnowledgeTools 检索
    │
    ├── 语义搜索（向量相似度）
    ├── 关键词搜索（全文匹配）
    └── 混合检索 → 返回相关文档片段
```

### 技术细节

| 组件 | 选型 |
|------|------|
| 嵌入模型 | BGE-M3（1024 维） |
| 向量数据库 | PostgreSQL pgvector |
| 文档解析 | flexmark（Markdown → 纯文本） |
| 检索策略 | 语义搜索 + 关键词搜索 混合 |
| 相似度算法 | 余弦相似度 |

---

## 🧠 上下文窗口管理

为高效管理长对话，系统实现了一套完整的 Token 感知上下文管理机制：

- **TokenAwareChatMemory**：替代 Spring AI 的 `MessageWindowChatMemory`，感知每轮对话的 Token 消耗
- **ContextWindowManager**：构建上下文窗口，自动筛选消息确保不超出模型上下文限制
- **ContextTruncator**：工具响应截断，防止工具返回的巨型数据撑爆上下文
- **BudgetAllocator**：Token 预算分配器，为系统提示词、对话历史、工具响应预留合理配额
- **记忆笔记**：定期自动生成对话摘要笔记，在长对话中保留关键信息
- **对话摘要**：支持将早期对话压缩为摘要，释放上下文空间

---

## 📡 API 概览

### Agent 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/agents` | 创建智能体 |
| `GET` | `/api/agents` | 获取所有智能体 |
| `GET` | `/api/agents/{id}` | 获取单个智能体 |
| `PATCH` | `/api/agents/{id}` | 更新智能体 |
| `DELETE` | `/api/agents/{id}` | 删除智能体 |

### 聊天会话

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat-sessions` | 创建聊天会话 |
| `GET` | `/api/chat-sessions` | 获取所有会话 |
| `GET` | `/api/chat-sessions/{id}` | 获取单个会话 |
| `GET` | `/api/chat-sessions/agent/{agentId}` | 按 Agent 获取会话 |
| `PATCH` | `/api/chat-sessions/{id}` | 更新会话 |
| `DELETE` | `/api/chat-sessions/{id}` | 删除会话 |

### 聊天消息

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat-messages` | 发送消息（触发 Agent 循环） |
| `GET` | `/api/chat-messages/session/{sessionId}` | 获取会话消息列表 |

### 知识库 & 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/knowledge-bases` | 创建知识库 |
| `GET` | `/api/knowledge-bases` | 获取所有知识库 |
| `GET` | `/api/knowledge-bases/{id}` | 获取单个知识库 |
| `PATCH` | `/api/knowledge-bases/{id}` | 更新知识库 |
| `DELETE` | `/api/knowledge-bases/{id}` | 删除知识库 |
| `POST` | `/api/documents/upload` | 上传 Markdown 文档 |
| `GET` | `/api/documents/kb/{kbId}` | 获取知识库文档列表 |

### 工具信息

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/tools` | 获取可用工具列表及分类 |

### 实时连接

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/sse/connect/{chatSessionId}` | SSE 实时消息流 |

---

## 🎨 前端界面

### 组件架构

```
App
└── JChatMindLayout
    ├── Sidebar
    │   ├── SideMenu（导航菜单）
    │   ├── AgentTabContent（智能体列表 + 创建）
    │   ├── ChatTabContent（聊天历史列表）
    │   └── KnowledgeBaseTabContent（知识库列表 + 创建）
    └── Content
        ├── AgentChatView（聊天主界面）
        │   ├── AgentChatHistory（消息历史，Markdown 渲染）
        │   ├── EmptyAgentChatView（空状态）
        │   └── AgentChatInput（输入框 + 发送）
        └── KnowledgeBaseView（知识库详情 + 文档上传）
```

### 技术亮点

- **@ant-design/x**：专为 AI 对话场景设计的组件库，内置消息气泡、思考过程展示
- **@ant-design/x-markdown**：流式 Markdown 渲染，支持代码高亮
- **react-contexify**：右键上下文菜单
- **SSE 客户端**：基于 `EventSource` 的实时消息接收

---

## 📊 RAG 评测

项目包含基于 [Ragas](https://docs.ragas.io/) 框架的 RAG 评测脚本，用于评估知识库检索质量。

```bash
cd rag测试脚本
pip install ragas pandas
python rag_evaluation_tester.py
```

### 评测指标

| 指标 | 分数 | 说明 |
|------|------|------|
| 上下文精确度（Context Precision） | **0.71** | 检索结果中相关内容的比例 |
| 上下文召回率（Context Recall） | **0.93** | 相关内容被检索到的比例 |

---

## 📝 License

MIT License

---

<p align="center">
  <strong>Built with ❤️ using Spring AI & React</strong>
</p>