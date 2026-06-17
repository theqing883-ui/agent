package com.kaer.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @TableName chat_session
 */
@Data
@Builder
public class ChatSession {
    private String id;

    private String agentId;

    private String title;

    /**
     * 父会话 ID，用于多 Agent 任务委派时关联父子会话。
     * 顶层会话此字段为 null，子会话指向创建它的父会话。
     */
    private String parentSessionId;

    /**
     * 会话类型：PARENT（顶层会话）或 CHILD（子任务会话）。
     * 默认为 PARENT。
     */
    private String sessionType;

    // JSON string
    private String metadata;//扩展内容，如输入语言、设备类型等信息

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        ChatSession other = (ChatSession) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
                && (this.getAgentId() == null ? other.getAgentId() == null : this.getAgentId().equals(other.getAgentId()))
                && (this.getTitle() == null ? other.getTitle() == null : this.getTitle().equals(other.getTitle()))
                && (this.getParentSessionId() == null ? other.getParentSessionId() == null : this.getParentSessionId().equals(other.getParentSessionId()))
                && (this.getSessionType() == null ? other.getSessionType() == null : this.getSessionType().equals(other.getSessionType()))
                && (this.getMetadata() == null ? other.getMetadata() == null : this.getMetadata().equals(other.getMetadata()))
                && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
                && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getAgentId() == null) ? 0 : getAgentId().hashCode());
        result = prime * result + ((getTitle() == null) ? 0 : getTitle().hashCode());
        result = prime * result + ((getParentSessionId() == null) ? 0 : getParentSessionId().hashCode());
        result = prime * result + ((getSessionType() == null) ? 0 : getSessionType().hashCode());
        result = prime * result + ((getMetadata() == null) ? 0 : getMetadata().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" +
                "Hash = " + hashCode() +
                ", id=" + id +
                ", agentId=" + agentId +
                ", title=" + title +
                ", parentSessionId=" + parentSessionId +
                ", sessionType=" + sessionType +
                ", metadata=" + metadata +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                "]";
    }
}