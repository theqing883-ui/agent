package com.kaer.service;

import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.request.CreateChatMessageRequest;
import com.kaer.model.request.UpdateChatMessageRequest;
import com.kaer.model.response.CreateChatMessageResponse;
import com.kaer.model.response.GetChatMessagesResponse;

import java.util.List;

public interface ChatMessageFacadeService {
    GetChatMessagesResponse getChatMessagesBySessionId(String sessionId);

    List<ChatMessageDTO> getChatMessagesBySessionIdRecently(String sessionId, int limit);


    CreateChatMessageResponse createChatMessage(ChatMessageDTO chatMessageDTO);

//    CreateChatMessageResponse agentCreateChatMessage(CreateChatMessageRequest request);

//    CreateChatMessageResponse appendChatMessage(String chatMessageId, String appendContent);

    void deleteChatMessage(String chatMessageId);

    void updateChatMessage(String chatMessageId, UpdateChatMessageRequest request);
    CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request);
}
