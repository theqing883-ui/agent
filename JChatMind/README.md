# JChatMind

<div align="center">

**基于 Spring AI 的智能体（Agent）平台**

多模型 · 多工具 · RAG 知识库 · MCP 协议 · 多 Agent 协作

</div>

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.8 |
| AI 框架 | Spring AI 1.1.0 |
| AI 模型 | DeepSeek (`deepseek-chat`) / 智谱 AI (`glm-4.6`) |
| 数据库 | PostgreSQL + Redis |
| ORM | MyBatis |
| 向量检索 | BGE-M3 嵌入 + pgvector |
| 实时推送 | SSE (Server-Sent Events) |
| 前端 | React 19 + TypeScript + Ant Design 6 + Tailwind CSS 4 |
| 构建 | Maven (后端) + Vite (前端) |
| 语言 | Java 17 |

---

## 项目结构

```
JChatMind/
├── jchatmind01/                          # 后端 Spring Boot 项目
│   ├── src/main/java/com/kaer/
│   │   ├── agent/                        # Agent 核心引擎
│   │   │   ├── JChatMind.java            #   Agent 主循环（Think→Execute）
│   │   │   ├── JChatMindFactory.java     #   Agent 工厂（装配依赖）
│   │   │   ├── AgentState.java           #   状态机：IDLE→THINKING→EXECUTING→FINISHED
│   │   │   ├── AgentContextHolder.java   #   ThreadLocal 会话上下文
│   │   │   ├── ConstantPrompt.java       #   系统提示词模板
│   │   │   ├── messagebus/               #   多 Agent 消息总线
│   │   │   ├── skill/                    #   技能系统（Markdown 技能脚本）
│   │   │   └── tools/                    #   工具定义（23 个工具）
│   │   ├── config/                       # Spring 配置（ChatClient、Redis、CORS、异步）
│   │   ├── context/                      # 上下文窗口管理
│   │   │   ├── budget/                   #   Token 预算分配器
│   │   │   ├── cache/                    #   Redis 缓存服务 & 拦截器
│   │   │   ├── compact/                  #   四级压缩流水线（L3→L2→L1→L4）
│   │   │   ├── manager/                  #   上下文窗口管理器
│   │   │   ├── memory/                   #   Token 感知聊天记忆
│   │   │   ├── model/                    #   上下文数据模型
│   │   │   ├── token/                    #   Token 估算器
│   │   │   └── truncator/                #   内容截断器
│   │   ├── controller/                   # REST 控制器（7 个）
│   │   ├── converter/                    # 对象转换器
│   │   ├── event/                        # 事件驱动（ChatEvent→Listener）
│   │   ├── mapper/                       # MyBatis Mapper
│   │   ├── message/                      # SSE 消息体
│   │   ├── model/                        # 数据模型（entity/dto/vo/request/response）
│   │   ├── resilience/                   # 韧性引擎（错误恢复 & 模型备胎切换）
│   │   └── service/                      # 业务服务层
│   ├── src/main/resources/
│   │   ├── application.yml               # 主配置
│   │   ├── mapper/                       # MyBatis XML
│   │   └── skills/                       # 技能 Markdown 脚本
│   └── mcp/                              # MCP Server（Bocha 搜索）
│
├── ui/                                   # 前端 React 项目
│   └── src/
│       ├── api/                          # API 层
│       ├── components/                   # UI 组件
│       ├── hooks/                        # 自定义 Hooks
│       └── types/                        # TypeScript 类型
│
└── README.md
```

---

## 后端架构

### 1. Agent 主循环

核心类是 `JChatMind`，实现 **Think → Execute** 自主循环，最多 20 步。

```
用户消息
  │
  ▼
┌─────────────────────────────────────────────┐
│  JChatMind.run()                            │
│                                             │
│  ┌─────────┐    有工具调用     ┌──────────┐  │
│  │ think() │ ───────────────→ │ execute()│  │
│  │ (LLM)   │ ←─────────────── │ (工具执行) │  │
│  └─────────┘   工具结果回传    └──────────┘  │
│       │                                     │
│       │ 无工具调用                            │
│       ▼                                     │
│  FINISHED                                   │
└─────────────────────────────────────────────┘
  │
  ▼
SSE 实时推送到前端
```

**关键设计：**
- 每轮 `think()` 前调用 `ContextWindowManager.buildContextWindow()` 构建上下文窗口，包含四级压缩
- 工具执行结果立即通过 SSE 推送，前端可实时看到 AI 的"思考过程"
- `AgentContextHolder` 基于 ThreadLocal 传递 `sessionId` / `agentId`，工具层无需显式传参

### 2. 上下文窗口管理（核心子系统）

这是整个系统最复杂的部分，负责在有限的 Token 预算内装入尽可能多的有效信息。

#### 2.1 预算分配

`BudgetAllocator` 将模型总 Token 预算（默认 128K）分配给：系统提示词、工具定义、消息历史、记忆笔记。

#### 2.2 四级压缩流水线

`CompactorPipeline` 按 **L3 → L2 → L1 → L4** 顺序执行，每级检查预算是否满足，满足即短路退出。

```
原始消息（全量）
  │
  ├── L3: 单条工具响应截断      ← 超长工具结果 → Redis 缓存，原地截断
  │    └── 预算满足？→ 退出
  │
  ├── L2: 历史工具结果清理      ← 旧结果 → Redis 归档（7天TTL），占位替换
  │    └── 预算满足？→ 退出
  │
  ├── L1: 头尾保留滑动窗口      ← 保留开头 K 条 + 末尾 N 条，中间丢弃
  │    └── 预算满足？→ 退出
  │
  └── L4: LLM 全量摘要兜底      ← 调用廉价模型压缩对话历史
       └── 必定满足（或返回原始消息）
```

| 级别 | 组件 | 策略 | 触发条件 |
|------|------|------|----------|
| L3 | `L3SingleToolResponseGate` | 单条工具响应 > 阈值 → 截断+Redis缓存 | 工具响应 > 4000 tokens |
| L2 | `L2HistoricalToolResultCleanup` | 历史工具结果 > M 条 → 旧结果归档 Redis | ToolResponse > 5 条 |
| L1 | `L1SlidingWindowTrim` | 保留头尾，丢弃中间消息 | Token 超预算 |
| L4 | `L4FullSummaryFallback` | 调用 LLM 生成对话摘要，替换记忆 | Token 严重超预算 |

#### 2.3 Token 估算

`TokenCounter` 基于 CJK 字符 vs 拉丁字符的启发式算法估算 Token 数：

- CJK 字符：1 字符 ≈ 1.5 token
- 拉丁/数字：1 字符 ≈ 0.3 token
- 支持工具调用、图片等特殊消息类型

#### 2.4 记忆笔记

`MemoryNoteStoreServiceImpl` 定期将最近的对话片段压缩为持久化笔记。每次构建上下文时，相关笔记自动注入为系统消息。

### 3. 工具系统

#### 3.1 工具分类

| 类型 | 说明 |
|------|------|
| `FIXED` | 所有 Agent 默认拥有，不可禁用（`readCache`、`terminate`） |
| `OPTIONAL` | 创建 Agent 时按需启用（数据库查询、邮件、委派等） |
| MCP 工具 | 通过 MCP 协议动态连接的外部工具 |

#### 3.2 固定工具

| 工具 | 用途 |
|------|------|
| `ReadCacheTool` | 读取被 L2/L3 缓存到 Redis 的工具响应，支持分页 |
| `TerminateTool` | Agent 任务完成后主动结束循环 |
| `DirectAnswerTool` | 直接向用户输出最终答案 |
| `SkillTool` | 加载并执行技能脚本 |

#### 3.3 可选工具（部分）

| 工具 | 用途 |
|------|------|
| `DataBaseTools` | PostgreSQL 只读查询，结果以 Markdown 表格返回 |
| `KnowledgeTools` | 知识库语义检索（RAG） |
| `EmailTools` | SMTP 邮件发送 |
| `FileSystemTools` | 文件读写 |
| `CreateTaskTool` / `ListTaskTool` / `ClaimTaskTool` / `CompleteTaskTool` | 多 Agent 任务管理 |
| `SpawnTeammateTool` / `SendMessageTool` / `StopTeammateTool` | 多 Agent 协作 |
| `BochaSearchTool` (MCP) | 博查联网搜索 |

#### 3.4 工具注册流程

```
1. JChatMindFactory.resolveRuntimeTools()
      │
      ├── 收集所有 ToolType.FIXED 的 Bean
      ├── 根据 Agent 配置添加 ToolType.OPTIONAL
      └── 连接 MCP Server，获取远程工具定义
      │
      ▼
2. buildToolCallbacks()
      │
      └── 扫描 @Tool 注解方法 → 注册为 Spring AI ToolCallback
      │
      ▼
3. JChatMind.think()
      │
      └── ChatClient.call().tools(availableTools) → LLM 自主决策调用
```

### 4. Redis 缓存层

#### 4.1 缓存架构

```
ToolResponseCacheInterceptor (全局缓存入口)
  │
  ├── 工具结果 ≤ 4000 tokens → 原样通过
  └── 工具结果 > 4000 tokens
        │
        ├── ToolResultCacheService.store() → Redis SETEX
        ├── 内容截断 + 截断提示（含 cacheId）
        └── LLM 按需调用 readCache 工具取回
```

#### 4.2 两级 TTL

| 场景 | TTL | 配置键 |
|------|-----|--------|
| 普通工具结果缓存 | 10 分钟 | `jchatmind.tool-cache.ttl-seconds` |
| L2 历史归档 | 7 天 | `jchatmind.context-compactor.l2.cache-ttl-seconds` |

#### 4.3 Redis Key 格式

```
jchatmind:toolcache:{sessionId}:{toolName}:{8-hex-UUID}        # 数据
jchatmind:toolcache:meta:{sessionId}:{toolName}:{8-hex-UUID}   # 元数据
```

#### 4.4 TTL 续期

每次 `readCache` 读取成功后自动刷新 TTL，防止 LLM 分页读取中途过期。

### 5. 知识库（RAG）

```
Markdown 文档上传
  │
  ▼
DocumentParser → 文本分块（可配置大小和重叠）
  │
  ▼
BGE-M3 Embedding → 768 维向量
  │
  ▼
PostgreSQL pgvector 存储
  │
  ▼
Agent 对话时: 用户问题 → 向量相似度搜索 → Top-K 结果注入上下文
```

| 组件 | 说明 |
|------|------|
| 嵌入模型 | BGE-M3（通过智谱 API） |
| 向量维度 | 768 |
| 检索方式 | pgvector cosine 相似度 + HNSW 索引 |
| 分块策略 | 可配置块大小和块重叠 |

### 6. 多 Agent 协作

#### 6.1 消息总线（MessageBus）

```
┌──────────────────────────────────────────────┐
│              MessageBusImpl                   │
│                                              │
│  ConcurrentHashMap<String, Deque<Msg>>       │
│                                              │
│  agent-A → send(to="agent-B", msg) → 入队     │
│  agent-B → poll() → 取出消息                  │
└──────────────────────────────────────────────┘
```

- 每个 Agent 拥有独立的消息队列
- 非阻塞轮询，每次 `think()` 前检查收件箱
- 支持 parent→child / child→parent / peer→peer 三种通信模式

#### 6.2 任务委派机制

```
Parent Agent
  │
  ├── createTask() ──→ 任务创建，状态 PENDING
  ├── spawnTeammate() ──→ 启动子 Agent，传入任务
  │
  │   Child Agent
  │     ├── claimTask()     ← 认领任务
  │     ├── ...执行工作...
  │     ├── completeTask()  ← 标记完成
  │     ├── sendMessage()   ← 向 Parent 汇报
  │     └── terminate()     ← 自毁
  │
  ├── listTask()  ← 查看进度
  └── stopTeammate() ← 终止子 Agent
```

### 7. 韧性引擎（Error Recovery）

`ErrorRecoveryEngine` 提供 LLM 调用失败时的自动恢复：

```
调用失败
  │
  ├── IOException / 网络超时 → 指数退避重试（最多 3 次）
  │
  ├── 429 限流 → 等待 Retry-After → 重试
  │
  ├── 400 业务错误 → 提取错误信息 → 反馈给 Agent
  │
  └── 529 Overload → 切换备选模型（如 deepseek→glm）
       │
       └── RecoveryState 跟踪恢复状态 → 成功或最终失败
```

### 8. 技能系统

技能是存放在 `resources/skills/` 下的 Markdown 文件，在创建 Agent 时加载。

```markdown
<!-- 示例: task-management.md -->
# 任务管理技能
当需要分解复杂任务时，遵循以下步骤：
1. 分析任务依赖关系
2. 创建子任务并设置 blockedBy
3. 为每个任务分配合适的工具
...
```

- `SkillTool` 将技能内容注入系统提示词
- 前端可查看和启用/禁用技能
- 支持热加载，无需重启

---

## 配置参考

```yaml
# application.yml 关键配置段

spring:
  ai:
    deepseek:                          # DeepSeek 模型
      api-key: ${API-KEY-deepseek}
      chat.options.model: deepseek-chat
    zhipuai:                           # 智谱 AI 模型（备选）
      api-key: ${API-KEY-zhipuai}
      chat.options.model: glm-4.6

jchatmind:
  tool-cache:
    enabled: true                      # 工具响应缓存总开关
    ttl-seconds: 600                   # 普通缓存 TTL（10 分钟）
    trigger-tokens: 4000               # 触发缓存的 Token 阈值
    shown-tokens: 2000                 # 截断后展示的 Token 数
    read-max-chars: 16000              # readCache 单次最大读取字符数

  context-compactor:
    enabled: true                      # 上下文压缩总开关
    l1:
      keep-first-k: 10                 # L1 滑动窗口保留开头条数
      keep-last-n: 30                  # L1 滑动窗口保留末尾条数
    l2:
      max-kept-results: 5              # L2 保留最近工具结果条数
      cache-ttl-seconds: 604800        # L2 归档 Redis TTL（7 天）
    l4:
      enabled: true                    # L4 LLM 摘要开关
      cheap-model: null                # 廉价模型，null=用主模型
```

---

## API 参考

### Agent 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/agents` | 创建 Agent |
| `GET` | `/api/agents` | 查询所有 Agent |
| `PATCH` | `/api/agents/{agentId}` | 更新 Agent |
| `DELETE` | `/api/agents/{agentId}` | 删除 Agent |

### 聊天会话

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat-sessions` | 创建会话 |
| `GET` | `/api/chat-sessions` | 查询所有会话 |
| `GET` | `/api/chat-sessions/{sessionId}` | 查询单个会话 |
| `DELETE` | `/api/chat-sessions/{sessionId}` | 删除会话 |

### 聊天消息

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat-messages` | 发送消息（触发 Agent 循环） |
| `GET` | `/api/chat-messages/session/{sessionId}` | 查询会话消息列表 |

### 知识库 & 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/knowledge-bases` | 创建知识库 |
| `GET` | `/api/knowledge-bases` | 查询所有知识库 |
| `POST` | `/api/documents/upload` | 上传 Markdown 文档 |
| `GET` | `/api/documents/kb/{kbId}` | 查询知识库文档列表 |

### 实时推送

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/sse/connect/{chatSessionId}` | SSE 实时消息流 |

### SSE 消息类型

```
AI_PLANNING          — Agent 正在规划下一步
AI_THINKING          — Agent 正在思考（LLM 调用中）
AI_EXECUTING         — Agent 正在执行工具
AI_GENERATED_CONTENT  — AI 生成的文本内容（流式）
AI_DONE              — Agent 任务完成
```

---

## 快速开始

### 环境要求

- JDK 17+ / Maven 3.8+
- Node.js 18+
- PostgreSQL（需安装 pgvector 扩展）
- Redis

### 1. 数据库初始化

```sql
CREATE DATABASE jchatmind;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 环境变量

```bash
export API-KEY-deepseek=sk-your-key       # 必填
export API-KEY-zhipuai=your-key           # 可选，备选模型
export API-KEY-bocha-search=your-key      # 可选，联网搜索
export QQ-SMTP=your-code                  # 可选，邮件工具
```

### 3. 启动后端

```bash
cd jchatmind01
mvn spring-boot:run
# → http://localhost:8080
```

### 4. 启动前端

```bash
cd ui
npm install && npm run dev
# → http://localhost:5173
```

---

## 前端简介

前端基于 **React 19 + TypeScript + Ant Design 6 + Tailwind CSS 4**，Vite 构建。

**主要页面：**

| 页面 | 功能 |
|------|------|
| Agent 管理 | 创建/编辑/删除 Agent，配置模型、工具、知识库 |
| 聊天视图 | Agent 对话界面，展示思考过程（SSE 流式渲染） |
| 知识库管理 | 创建知识库，上传 Markdown 文档 |
| 聊天记录 | 查看/切换/删除历史会话 |

**技术要点：**
- `@ant-design/x` 组件用于对话气泡和流式内容渲染
- SSE 通过 `EventSource` 连接 `/sse/connect/{sessionId}`
- `React Context` 管理全局 Agent/会话状态
- API 请求通过 Vite 代理转发到后端 `localhost:8080`

---

<div align="center">

**Built with ❤️ using Spring AI & React**

</div>
