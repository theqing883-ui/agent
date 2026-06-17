package com.kaer.controller;

import com.kaer.model.common.ApiResponse;
import com.kaer.model.request.CreateChatMessageRequest;
import com.kaer.model.request.UpdateChatMessageRequest;
import com.kaer.model.response.CreateChatMessageResponse;
import com.kaer.model.response.GetChatMessagesResponse;
import com.kaer.service.ChatMessageFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api")
public class ChatMessageController {
    private final ChatMessageFacadeService chatMessageFacadeService;

    // 创建聊天消息
    @PostMapping("/chat-messages")
    public ApiResponse<CreateChatMessageResponse> sendMessage(@RequestBody CreateChatMessageRequest request) {
        return ApiResponse.success(chatMessageFacadeService.createChatMessage(request));
    }

    // 根据 sessionId 查询聊天消息
    @GetMapping("/chat-messages/session/{sessionId}")
    public ApiResponse<GetChatMessagesResponse> getChatMessagesBySessionId(@PathVariable String sessionId) {
        return ApiResponse.success(chatMessageFacadeService.getChatMessagesBySessionId(sessionId));
    }

    // 删除聊天消息
    @DeleteMapping("/chat-messages/{chatMessageId}")
    public ApiResponse<Void> deleteChatMessage(@PathVariable String chatMessageId) {
        chatMessageFacadeService.deleteChatMessage(chatMessageId);
        return ApiResponse.success();
    }

    // 更新聊天消息
    @PutMapping("/chat-messages/{chatMessageId}")
    public ApiResponse<Void> updateChatMessage(@PathVariable String chatMessageId, @RequestBody UpdateChatMessageRequest request) {
        chatMessageFacadeService.updateChatMessage(chatMessageId, request);
        return ApiResponse.success();
    }
}































