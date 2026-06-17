-- 多 Agent 任务委派：为 chat_session 表增加父子会话关联字段
ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS parent_session_id UUID REFERENCES chat_session(id),
    ADD COLUMN IF NOT EXISTS session_type VARCHAR(10) NOT NULL DEFAULT 'PARENT';

CREATE INDEX IF NOT EXISTS idx_chat_session_parent_id ON chat_session(parent_session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_type ON chat_session(session_type);
