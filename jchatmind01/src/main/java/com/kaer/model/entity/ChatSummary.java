package com.kaer.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatSummary {
    private String id;
    private String sessionId;
    private String content;
    private List<String> messageIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}