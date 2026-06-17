package com.kaer.service;

import com.kaer.model.request.CreateChatSessionRequest;
import com.kaer.model.request.UpdateChatSessionRequest;
import com.kaer.model.response.CreateChatSessionResponse;
import com.kaer.model.response.GetChatSessionResponse;
import com.kaer.model.response.GetChatSessionsResponse;

public interface ChatSessionFacadeService {
    GetChatSessionsResponse getChatSessions();

    GetChatSessionResponse getChatSession(String chatSessionId);

    GetChatSessionsResponse getChatSessionsByAgentId(String agentId);

    CreateChatSessionResponse createChatSession(CreateChatSessionRequest request);

    void deleteChatSession(String chatSessionId);

    void updateChatSession(String chatSessionId, UpdateChatSessionRequest request);

}
