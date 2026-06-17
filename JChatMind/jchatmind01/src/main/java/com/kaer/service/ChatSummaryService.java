package com.kaer.service;

import com.kaer.model.entity.ChatMessage;

import java.util.List;

public interface ChatSummaryService {
    String updateSessionSummary(String sessionId, List<ChatMessage> evictedMessages);
}
