package com.kaer.context.model;

public record TokenUsage(
        int totalTokens,
        int systemPromptTokens,
        int toolDefinitionsTokens,
        int messageTokens,
        int summaryTokens,
        int memoryNoteTokens
) {

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0, 0, 0);
    }
}
