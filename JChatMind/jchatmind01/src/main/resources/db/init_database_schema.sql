-- 数据库初始化脚本 (PostgreSQL)
-- 此脚本基于先前的表结构设计文档生成
-- 注意: 为了支持 `vector` 类型，需要预先在 PostgreSQL 中安装 `pgvector` 扩展。
-- 为了支持 `uuid` 生成，如果 PostgreSQL 版本较旧，可能需要启用 `pgcrypto`。

-- 启用必要的扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector"; -- 用于 RAG 的向量存储

-- ==========================================
-- 1. 独立的基础表 (无外键依赖)
-- ==========================================

-- 知识库表 (knowledge_base)
CREATE TABLE IF NOT EXISTS knowledge_base (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT,
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

-- 代理表 (agent)
CREATE TABLE IF NOT EXISTS agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT,
    description TEXT,
    system_prompt TEXT,
    model TEXT,
    allowed_tools JSONB,
    allowed_kbs JSONB,
    chat_options JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);


-- ==========================================
-- 2. 依赖于 agent 和 knowledge_base 的表
-- ==========================================

-- 聊天会话表 (chat_session)
CREATE TABLE IF NOT EXISTS chat_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID REFERENCES agent(id) ON DELETE SET NULL,
    title TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    parent_session_id UUID REFERENCES chat_session(id) ON DELETE CASCADE,
    session_type VARCHAR(10) DEFAULT 'PARENT'
);
CREATE INDEX IF NOT EXISTS idx_chat_session_parent_id ON chat_session (parent_session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_type ON chat_session (session_type);

-- 文档表 (document)
CREATE TABLE IF NOT EXISTS document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID REFERENCES knowledge_base(id) ON DELETE CASCADE,
    filename TEXT,
    filetype TEXT,
    size BIGINT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);


-- ==========================================
-- 3. 依赖于 chat_session 和 document 的表
-- ==========================================

-- 聊天消息表 (chat_message)
CREATE TABLE IF NOT EXISTS chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES chat_session(id) ON DELETE CASCADE,
    role TEXT,
    content TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);

-- 聊天记忆笔记表 (chat_memory_note)
CREATE TABLE IF NOT EXISTS chat_memory_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID  REFERENCES chat_session(id) ON DELETE CASCADE,
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_memory_note_session ON chat_memory_note (session_id);

-- 聊天总结表 (chat_summary)
CREATE TABLE IF NOT EXISTS chat_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID UNIQUE REFERENCES chat_session(id) ON DELETE CASCADE,
    content TEXT DEFAULT '',
    message_ids JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_summary_session ON chat_summary (session_id);

-- 文档向量分块表 (chunk_bge_m3)
CREATE TABLE IF NOT EXISTS chunk_bge_m3 (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID REFERENCES knowledge_base(id) ON DELETE CASCADE,
    doc_id UUID REFERENCES document(id) ON DELETE CASCADE,
    content TEXT,
    metadata JSONB,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    fts TSVECTOR DEFAULT to_tsvector('simple', '')
);

-- 为 fts 创建全文检索 GIN 索引
CREATE INDEX IF NOT EXISTS idx_chunk_bge_m3_fts ON chunk_bge_m3 USING GIN (fts);

-- 为 vector 向量数据创建近似最近邻 (ANN) 索引 (HNSW)
-- 注意: 使用 vector_l2_ops 为 L2 距离, vector_ip_ops 为内积, vector_cosine_ops 为余弦相似度
-- 根据您的具体模型特性和查询习惯选择。BGE-M3 通常使用内积或余弦距离。
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON chunk_bge_m3 USING hnsw (embedding vector_cosine_ops);
