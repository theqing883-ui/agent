package com.kaer.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMemoryNote {
    private String id;
    private String sessionId;
    private String content;
    private LocalDateTime createdAt;
}
