---
name: database-schema-design
description: 数据库表结构设计说明文档，包含 AI 代理、聊天会话、知识库以及检索增强生成 (RAG) 相关的表结构与索引说明。
---

# 数据库表结构设计说明文档 (Database Schema Specification)

本文档根据数据库表结构描述，整理并生成对应的 Markdown 表格，以便于阅读、开发与维护。

---

## 1. agent 表
**描述**：存储 AI 代理的配置信息。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，唯一标识符 |
| **name** | text | - | 代理名称 |
| **description** | text | - | 代理详细描述 |
| **system_prompt** | text | - | 系统提示词 (System Prompt) |
| **model** | text | - | 所使用的语言模型名称 |
| **allowed_tools** | jsonb | - | 允许该代理使用的工具列表 (JSON 格式) |
| **allowed_kbs** | jsonb | - | 允许该代理访问的知识库列表 (JSON 格式) |
| **chat_options** | jsonb | - | 聊天相关配置选项 (JSON 格式) |
| **created_at** | timestamp | `now()` | 记录创建时间 |
| **updated_at** | timestamp | `now()` | 记录更新时间 |

### 键与索引 (Keys & Indexes)
| 类型 | 索引名称 | 涉及字段 | 约束 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `agent_pkey` | `id` | 唯一标识一行代理配置 |
| **唯一索引 (Unique Index)** | `agent_pkey` | `id` | 确保 ID 唯一性 |

---

## 2. chat_memory_note 表
**描述**：存储聊天会话的记忆节点或笔记。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，唯一标识符 |
| **session_id** | uuid | - | 会话 ID，关联聊天会话 |
| **content** | text | - | 记忆节点或笔记的具体内容 |
| **created_at** | timestamp | `CURRENT_TIMESTAMP` | 记录创建时间 |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 关联目标 / 备注                        |
| :--- | :--- | :--- |:---------------------------------|
| **主键 (Primary Key)** | `chat_memory_note_pkey` | `id` | 唯一标识一行记忆笔记                       |
| **外键 (Foreign Key)** | `fk_chat_memory_note_session_id` | `session_id` | 关联至 `chat_session (id)`          |
| **唯一索引 (Unique Index)** | `chat_memory_note_pkey` | `id` | 自动生成的主键索引                        |
| **常规索引 (Index)** | `idx_memory_note_session` | `session_id` | 提升基于会话 ID 的查询效率                    |

---

## 3. chat_message 表
**描述**：存储聊天会话中的具体消息记录（上下文历史）。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，唯一标识符 |
| **session_id** | uuid | - | 消息所属的会话 ID |
| **role** | text | - | 消息发送者角色 (例如: user, assistant, system) |
| **content** | text | - | 消息的具体文本内容 |
| **metadata** | jsonb | - | 消息的元数据 (如 token 消耗、打分等，JSON 格式) |
| **created_at** | timestamp | `now()` | 消息创建时间 |
| **updated_at** | timestamp | `now()` | 消息更新时间 |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 关联目标 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `chat_message_pkey` | `id` | 唯一标识一条消息 |
| **外键 (Foreign Key)** | `chat_message_session_id_fkey` | `session_id` | 关联至 `chat_session (id)` |
| **唯一索引 (Unique Index)** | `chat_message_pkey` | `id` | 自动生成的主键索引 |

---

## 4. chat_session 表
**描述**：存储聊天会话的基本信息，支持父子会话层级结构（如分支对话或追问）。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，会话唯一标识符 |
| **agent_id** | uuid | - | 该会话所使用的 AI 代理 ID |
| **title** | text | - | 会话标题 |
| **metadata** | jsonb | - | 会话元数据 (JSON 格式) |
| **created_at** | timestamp | `now()` | 会话创建时间 |
| **updated_at** | timestamp | `now()` | 会话最近更新时间 |
| **parent_session_id** | uuid | - | 父会话 ID，用于支持多层级/分支对话 |
| **session_type** | varchar(10) | `'PARENT'` | 会话类型 (例如: PARENT, CHILD 等) |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 关联目标 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `chat_session_pkey` | `id` | 唯一标识一个会话 |
| **外键 (Foreign Key) 1** | `chat_session_agent_id_fkey` | `agent_id` | 关联至 `agent (id)` |
| **外键 (Foreign Key) 2** | `chat_session_parent_session_id_fkey` | `parent_session_id` | 关联至 `chat_session (id)` (自引用) |
| **唯一索引 (Unique Index)** | `chat_session_pkey` | `id` | 自动生成的主键索引 |
| **常规索引 (Index)** | `idx_chat_session_parent_id` | `parent_session_id` | 加快父子会话树形结构查询 |
| **常规索引 (Index)** | `idx_chat_session_type` | `session_type` | 加快按会话类型筛选的查询 |

---

## 5. chat_summary 表
**描述**：存储聊天会话的阶段性总结信息（用于缩减长期上下文开销）。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，唯一标识符 |
| **session_id** | uuid | - | 总结所属的会话 ID |
| **content** | text | `''::text` | 总结的具体文本内容 |
| **message_ids** | jsonb | `'[]'::jsonb` | 被包含在此次总结中的消息 ID 列表 |
| **created_at** | timestamp | `CURRENT_TIMESTAMP` | 总结生成时间 |
| **updated_at** | timestamp | `CURRENT_TIMESTAMP` | 总结更新时间 |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 关联目标 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `chat_summary_pkey` | `id` | 唯一标识一行总结记录 |
| **唯一约束 (Unique)** | `uq_chat_summary_session` | `session_id` | 每个会话拥有唯一的总结记录 |
| **外键 (Foreign Key)** | `fk_chat_summary_session` | `session_id` | 关联至 `chat_session (id)` |
| **唯一索引 (Unique Index)** | `chat_summary_pkey` | `id` | 主键唯一索引 |
| **唯一索引 (Unique Index)** | `uq_chat_summary_session` | `session_id` | 确保会话总结唯一性 |
| **常规索引 (Index)** | `idx_chat_summary_session` | `session_id` | 加快基于会话的总结查询 |

---

## 6. chunk_bge_m3 表
**描述**：存储文档的向量化分块数据 (Chunks)，用于检索增强生成 (RAG) 系统的向量检索与全文检索。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，切片唯一标识符 |
| **kb_id** | uuid | - | 所属知识库 ID |
| **doc_id** | uuid | - | 所属文档 ID |
| **content** | text | - | 文本分块的具体切片内容 |
| **metadata** | jsonb | - | 切片元数据 (如页码、行号等，JSON 格式) |
| **embedding** | vector(1024) | - | 1024 维向量，存储 BGE-M3 模型生成的向量特征 |
| **created_at** | timestamp | `now()` | 切片创建时间 |
| **updated_at** | timestamp | `now()` | 切片更新时间 |
| **fts** | tsvector | `to_tsvector('simple'...)`| 全文检索向量 (Full-Text Search Vector)，用于关键词匹配 |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 关联目标 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `chunk_bge_m3_pkey` | `id` | 唯一标识一个切片 |
| **外键 (Foreign Key) 1** | `chunk_bge_m3_doc_id_fkey` | `doc_id` | 关联至 `document (id)` |
| **外键 (Foreign Key) 2** | `chunk_bge_m3_kb_id_fkey` | `kb_id` | 关联至 `knowledge_base (id)` |
| **唯一索引 (Unique Index)** | `chunk_bge_m3_pkey` | `id` | 主键唯一索引 |
| **全文检索索引 (FTS)** | `idx_chunk_bge_m3_fts` | `fts` | 倒排索引，用于加速关键词全文检索 |
| **向量索引 (Vector Index)** | `idx_chunk_embedding` | `embedding` | 向量数据库索引，用于加速相似度检索 (ANN) |

---

## 7. document 表
**描述**：存储知识库中导入的文档记录与元数据信息。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，文档唯一标识符 |
| **kb_id** | uuid | - | 文档所属的知识库 ID |
| **filename** | text | - | 文档文件名 |
| **filetype** | text | - | 文档文件类型 (如 pdf, md, txt, docx) |
| **size** | bigint | - | 文件大小 (字节数 Byte) |
| **metadata** | jsonb | - | 文档自定义元数据 (JSON 格式) |
| **created_at** | timestamp | `now()` | 文档上传/创建时间 |
| **updated_at** | timestamp | `now()` | 文档更新时间 |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 关联目标 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `document_pkey` | `id` | 唯一标识一个文档 |
| **外键 (Foreign Key)** | `document_kb_id_fkey` | `kb_id` | 关联至 `knowledge_base (id)` |
| **唯一索引 (Unique Index)** | `document_pkey` | `id` | 主键唯一索引 |

---

## 8. knowledge_base 表
**描述**：存储知识库的基本元信息。

### 列信息 (Columns)
| 字段名 | 数据类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **id** | uuid | `gen_random_uuid()` | 主键，知识库唯一标识符 |
| **name** | text | - | 知识库名称 |
| **description** | text | - | 知识库描述信息 |
| **metadata** | jsonb | - | 知识库元数据 (JSON 格式) |
| **created_at** | timestamp | `now()` | 知识库创建时间 |
| **updated_at** | timestamp | `now()` | 知识库修改时间 |

### 键与索引 (Keys & Indexes)
| 类型 | 名称 | 涉及字段 | 约束 / 备注 |
| :--- | :--- | :--- | :--- |
| **主键 (Primary Key)** | `knowledge_base_pkey` | `id` | 唯一标识一个知识库 |
| **唯一索引 (Unique Index)** | `knowledge_base_pkey` | `id` | 确保主键唯一性 |
