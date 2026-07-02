---
name: multi-agent-collaboration
description: 多 Agent 协作系统使用指南。当需要将复杂任务拆分并委派给后台队友并行执行、管理队友生命周期、或通过 MessageBus 进行跨 Agent 异步通信时，应加载此技能。
---

# 多 Agent 协作系统

## 架构概览

```
                         ┌──────────────────────┐
                         │     Task System       │
                         │  (任务拆解/依赖管理)    │
                         │  createTask/listTask/  │
                         │  claimTask/completeTask│
                         └──────────┬───────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    │                               │                               │
    ▼                               ▼                               ▼
┌─────────┐  spawnTeammate()  ┌──────────┐  spawnTeammate()  ┌──────────┐
│  Lead   │ ───────────────── │ Teammate │                   │ Teammate │
│ (主控)   │                  │ (后台工人) │                  │ (后台工人) │
│         │ ◄─────────────── │          │ ◄─────────────── │          │
│ pollIn- │  replyToLead()   │  JChatMind│  replyToLead()   │  JChatMind│
│ box()   │                  │ .run()    │                  │ .run()    │
└─────────┘                  └──────────┘                  └──────────┘
       ▲                           ▲                              ▲
       │                           │                              │
       └───────────────────────────┴──────────────────────────────┘
                      MessageBus (文件 JSONL + ReentrantLock)
                      lead.jsonl / alice.jsonl / bob.jsonl
```

**核心理念**：Lead 负责「拆解+调度」，Teammate 在自己的 think-execute 循环中「自我驱动工作」，MessageBus 承载异步通信，Task
系统协调依赖关系。

## 何时使用

- 用户请求涉及多个独立子任务，可以并行执行
- 需要后台持续运行的工人（如定时检查、长时间的数据处理）
- 任务之间存在依赖关系，需要 DAG 编排
- 单 Agent 的任务步骤数（maxSteps）不足以完成所有工作

## 工具清单

### 队友管理

| 工具                                                           | 用途             | 谁可以用     |
|--------------------------------------------------------------|----------------|----------|
| `spawnTeammate(teammateName, agentId?, customSystemPrompt?)` | 启动一个后台队友守护线程   | Lead     |
| `listTeammates(filterName?)`                                 | 查看当前会话的队友及运行状态 | 所有 Agent |

### 通信协议

| 工具                                    | 用途                                              | 谁可以用                      |
|---------------------------------------|-------------------------------------------------|---------------------------|
| `sendMessage(target, content, type?)` | 向其他 Agent 发消息；type 默认 REQUEST，requestId 由系统自动生成 | Lead 发指令；Teammate 做主动进度汇报 |
| `replyToLead(content, requestId)`     | 回复 Lead 的某个 REQUEST，requestId 必须与收到的 REQUEST 一致 | Teammate                  |

### 任务系统（详见 task-management 技能）

| 工具                                             | 用途                                             |
|------------------------------------------------|------------------------------------------------|
| `createTask(subject, description, blockedBy?)` | 创建任务，可选前置依赖                                    |
| `listTasks()`                                  | 查看所有任务（按 PENDING / IN_PROGRESS / COMPLETED 分组） |
| `getTask(taskId)`                              | 查看任务详情                                         |
| `claimTask(taskId, agentName)`                 | 认领 PENDING 且依赖已满足的任务                           |
| `completeTask(taskId, agentName)`              | 完成 IN_PROGRESS 的任务，自动解除后续任务阻塞                  |

## 标准工作流

### 模式 1：任务驱动（推荐）

```
Lead:
  1. 分析用户需求 → 拆解为子任务
  2. createTask("任务A", "...")              ← 无依赖，立即可执行
  3. createTask("任务B", "...", ["任务A"])   ← 依赖任务A
  4. createTask("任务C", "...", ["任务A"])   ← 依赖任务A，可与B并行
  5. spawnTeammate("researcher")             ← 唤醒工人
  6. spawnTeammate("coder")
  7. 等待队友汇报（pollInbox 自动注入收件箱消息到 chatMemory）

Teammate (自动执行):
  1. listTasks() → 发现 PENDING 且依赖已满足的任务
  2. getTask(taskId) → 了解详情
  3. claimTask(taskId, "researcher") → 认领
  4. 使用工具完成任务... (readFile, webSearch, writeFile, ...)
  5. completeTask(taskId, "researcher") → 标记完成
  6. sendMessage("lead", "任务A已完成: summary", "STATUS_UPDATE") → 主动汇报
  7. 循环回到第 1 步，检查是否有更多任务
  8. 无可用任务 → terminate（TeammateWorker 会在 2 秒后自动重启）
```

### 模式 2：指令驱动（Req-ACK 协议）

```
Lead:
  1. sendMessage("alice", "请执行关机检查", "REQUEST")
     → 返回: "消息已发送至 alice (requestId: abc12345)"
     → 系统自动注册 PendingRequest(id=abc12345, status=AWAITING)
  2. 继续其他工作...

Teammate (alice):
  1. pollInbox → 收到 "[REQUEST from lead (id: abc12345)]: 请执行关机检查"
  2. 执行关机检查...
  3. replyToLead("关机检查通过，可以安全关机", "abc12345")
     → 系统发送 RESPONSE 到 lead.jsonl，携带相同 requestId

Lead:
  1. pollInbox → 匹配 PendingRequest[id=abc12345]
     → status → RESPONDED
     → 注入 "[RESPONSE from alice for abc12345]: 关机检查通过，可以安全关机"
  2. think: 收到 Alice 的回复，继续下一步决策
```

## 通信协议详解

### Correlation ID 机制

每条 REQUEST 消息自动携带一个系统生成的 `requestId`（UUID 前 8 位），贯穿全链路：

- Lead 发 REQUEST → 工具层自动生成 requestId → 返回给 LLM 供参考
- Lead 发 REQUEST → 工具层自动注册 PendingRequest(status=AWAITING)
- Teammate 回复 → `replyToLead(content, requestId)` → 发送 RESPONSE（携带相同 requestId）
- Lead pollInbox → 按 requestId 匹配 PendingRequest → 状态更新为 RESPONDED

**LLM 不需要自己构造 requestId**——工具层全自动管理。

### 消息格式

收件箱消息通过 `pollInbox()` 自动注入 chatMemory，格式如下：

| 原始消息类型                       | 注入 chatMemory 的格式                        |
|------------------------------|------------------------------------------|
| REQUEST（有匹配 PendingRequest）  | `[REQUEST from alice (id: abc123)]: 请关机` |
| RESPONSE（有匹配 PendingRequest） | `[RESPONSE from alice for abc123]: 已关机`  |
| STATUS_UPDATE（无匹配）           | `[STATUS_UPDATE from worker]: 任务A已完成`    |

## 最佳实践

### 任务拆解原则

- 每个子任务应**单一职责**、**独立可执行**
- 通过 `blockedBy` 数组表达依赖，形成 DAG
- 任务描述应完整，包含必要的上下文，让 Teammate 无需向 Lead 追问

### 队友管理

- 单个会话最多 **3 个队友**（可通过 `agent.teammate.max-teammates` 配置）
- 使用 `listTeammates` 查看当前队友状态
- 给队友起有意义的名字：`researcher`（调研）、`coder`（编码）、`reviewer`（审查）

### Req-ACK 协议

- **Teammate 回复 Lead 的 REQUEST 必须用 `replyToLead`**，不要用 `sendMessage`
- **Teammate 主动汇报进度用 `sendMessage(type=STATUS_UPDATE)`**
- requestId 由系统管理，LLM 只需在 replyToLead 中传递收到的 requestId

### 常见陷阱

- ❌ 不要在 Teammate 的 customSystemPrompt 中包含 `spawnTeammate` 或 `delegateTask`（已被自动排除）
- ❌ 不要创建超过 3 个队友
- ❌ 不要让 Teammate 之间互相发送消息（它们应该通过 Task 系统和 Lead 协调）
- ❌ 认领前置原则：严禁在未成功认领任务（claimTask）的情况下尝试执行任务或调用 completeTask。
- ✅ 验证所有权：如果你发现 completeTask 报错“无权操作”，请先执行 getTask <taskId> 确认该任务当前状态及 owner 字段。如果
  owner 为空或不是你，请重新执行 claimTask。
- ✅ 依赖闭环：在尝试认领任务之前，务必通过 listTasks 确认该任务是否已被其他队友认领。
- ✅ 任务完成后务必同时 `completeTask` + `sendMessage(STATUS_UPDATE)`，让 Lead 感知进度
- ✅ 当无任务可做时，Teammate 应调用 `terminate`（系统会自动重启）
- 
