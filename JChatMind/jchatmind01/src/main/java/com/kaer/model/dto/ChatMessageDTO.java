package com.kaer.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatMessageDTO {
    private String id;

    private String sessionId;

    private RoleType role;

    private String content;

    private MetaData metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 消息元数据，存储工具调用或工具响应信息。
     * <p>
     * 使用显式的 @NoArgsConstructor / @AllArgsConstructor 确保 Jackson 反序列化时
     * 能正确解析泛型类型 {@code List<AssistantMessage.ToolCall>}，
     * 避免因 Lombok @Builder 生成的全参构造函数导致类型擦除问题。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetaData {
        private ToolResponseMessage.ToolResponse toolResponse;
        private List<AssistantMessage.ToolCall> toolCalls;
    }

    @Getter
    @AllArgsConstructor
    public enum RoleType {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool");

        @JsonValue
        private final String role;

        public static RoleType fromRole(String role) {
            for (RoleType value : values()) {
                if (value.role.equals(role)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Invalid role: " + role);
        }
    }
}
