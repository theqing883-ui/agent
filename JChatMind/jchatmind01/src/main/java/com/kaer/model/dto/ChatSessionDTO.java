package com.kaer.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionDTO {
    private String id;

    private String agentId;

    private String title;

    /**
     * 父会话 ID，用于多 Agent 任务委派时关联父子会话。
     */
    private String parentSessionId;

    /**
     * 会话类型：PARENT 或 CHILD。
     */
    private String sessionType;

    private MetaData metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
    }
}
