package com.kaer.context.model;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public record ContextWindow(
        List<Message> selectedMessages,
        List<String> memoryNotes,
        TokenUsage tokenUsage
) {

    public static ContextWindow empty() {
        return new ContextWindow(List.of(), null, TokenUsage.zero());
    }
}
