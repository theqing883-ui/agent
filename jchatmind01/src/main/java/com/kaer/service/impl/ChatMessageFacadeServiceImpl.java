package com.kaer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.converter.ChatMessageConverter;
import com.kaer.event.ChatEvent;
import com.kaer.exception.BizException;
import com.kaer.mapper.ChatMessageMapper;
import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.entity.ChatMessage;
import com.kaer.model.request.CreateChatMessageRequest;
import com.kaer.model.request.UpdateChatMessageRequest;
import com.kaer.model.response.CreateChatMessageResponse;
import com.kaer.model.response.GetChatMessagesResponse;
import com.kaer.model.vo.ChatMessageVO;
import com.kaer.service.ChatMessageFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatMessageFacadeServiceImpl implements ChatMessageFacadeService {
    private final ChatMessageConverter chatMessageConverter;
    private final ChatMessageMapper chatMessageMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 根据会话ID获取完整聊天消息列表
     * <p>
     * 查询指定会话的所有聊天消息，并转换为VO返回给前端。
     * 如果会话不存在或无消息，抛出业务异常。
     *
     * @param sessionId 会话唯一标识
     * @return 包含聊天消息列表的响应对象
     * @throws BizException 当会话不存在时抛出
     */
    @Override
    public GetChatMessagesResponse getChatMessagesBySessionId(String sessionId) {
        // 从数据库查询会话的所有消息
        List<ChatMessage> chatMessages = chatMessageMapper.selectBySessionId(sessionId);
        // 校验会话是否存在
        if (chatMessages == null || chatMessages.isEmpty()) {
            throw new BizException("会话不存在");
        }

        // 将实体列表转换为VO列表
        List<ChatMessageVO> result = new ArrayList<ChatMessageVO>();
        for (ChatMessage chatMessage : chatMessages) {
            try {
                result.add(chatMessageConverter.toVO(chatMessage));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        // 构建并返回响应
        return GetChatMessagesResponse.builder()
                .chatMessages(result.toArray(new ChatMessageVO[0]))
                .build();
    }

    /**
     * 根据会话ID获取最近的聊天消息
     * <p>
     * 查询指定会话最近的N条消息，用于构建Agent的记忆上下文。
     * 适用于需要限制上下文长度的场景。
     *
     * @param sessionId 会话唯一标识
     * @param limit     返回消息数量限制
     * @return 聊天消息DTO列表（按时间顺序）
     */
    @Override
    public List<ChatMessageDTO> getChatMessagesBySessionIdRecently(String sessionId, int limit) {
        // 从数据库查询会话最近的limit条消息
        List<ChatMessage> chatMessages = chatMessageMapper.selectBySessionIdRecently(sessionId, limit);

        // 将实体列表转换为DTO列表
        List<ChatMessageDTO> result = new ArrayList<ChatMessageDTO>();
        for (ChatMessage chatMessage : chatMessages) {
            try {
                result.add(chatMessageConverter.toDTO(chatMessage));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * 创建聊天消息（DTO入口）
     * <p>
     * 通过DTO创建聊天消息，调用核心创建方法完成持久化。
     *
     * @param chatMessageDTO 聊天消息数据传输对象
     * @return 创建成功的响应，包含新消息ID
     */
    @Override
    public CreateChatMessageResponse createChatMessage(ChatMessageDTO chatMessageDTO) {
        // 调用核心创建方法
        ChatMessage chatMessage = doCreateChatMessage(chatMessageDTO);
        // 构建并返回响应
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    @Override
    public void deleteChatMessage(String chatMessageId) {
        ChatMessage chatMessage = chatMessageMapper.selectById(chatMessageId);
        if (chatMessage == null) {
            throw new BizException("消息不存在");
        }
        int result = chatMessageMapper.deleteById(chatMessageId);
        if (result == 0) {
            throw new BizException("删除失败");
        }
    }

    // 更新聊天消息
    @Override
    public void updateChatMessage(String chatMessageId, UpdateChatMessageRequest request) {
        try {
            // 查询数据库是否存在该消息
            ChatMessage existingchatMessage = chatMessageMapper.selectById(chatMessageId);
            if (existingchatMessage == null) {
                throw new BizException("聊天消息不存在" + chatMessageId);
            }
            ChatMessageDTO chatMessageDTO = chatMessageConverter.toDTO(existingchatMessage);
            // 更新content和元数据
            chatMessageConverter.updateDTOFromRequest(chatMessageDTO, request);

            ChatMessage updatedchatMessage = chatMessageConverter.toEntity(chatMessageDTO);

            // 保留原有的 ID、sessionId、role 和创建时间
            updatedchatMessage.setId(existingchatMessage.getId());
            updatedchatMessage.setUpdatedAt(LocalDateTime.now());
            updatedchatMessage.setCreatedAt(existingchatMessage.getCreatedAt());
            updatedchatMessage.setRole(existingchatMessage.getRole());
            updatedchatMessage.setSessionId(existingchatMessage.getSessionId());
            // 更新数据库
            int result = chatMessageMapper.updateById(updatedchatMessage);
            if (result <= 0) {
                throw new BizException("更新聊天消息失败");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建聊天消息
     * <p>
     * 该方法负责创建聊天消息，包括持久化消息数据和发布聊天事件。
     *
     * @param request 创建聊天消息的请求参数，包含消息内容、会话ID、代理ID等信息
     * @return 创建成功后的响应对象，包含新创建消息的ID
     */
    @Override
    public CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request) {
        // 调用内部方法执行消息创建的核心逻辑（包括数据转换、持久化等）
        ChatMessage chatMessage = doCreateChatMessage(request);

        // 发布聊天事件，通知其他模块有新消息产生
        applicationEventPublisher.publishEvent(new ChatEvent(
                        request.getAgentId(),
                        chatMessage.getSessionId(),
                        chatMessage.getContent()
                )
        );

        // 构建并返回响应，包含新创建消息的ID
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    /**
     * 创建聊天消息（入口方法）
     * <p>
     * 将请求对象转换为DTO后，调用核心创建方法。
     *
     * @param request 创建聊天消息的请求参数
     * @return 创建成功的聊天消息实体
     */
    private ChatMessage doCreateChatMessage(CreateChatMessageRequest request) {
        // 将请求对象转换为DTO对象
        ChatMessageDTO chatMessageDTO = chatMessageConverter.toDTO(request);
        // 调用核心创建方法
        return doCreateChatMessage(chatMessageDTO);
    }

    /**
     * 创建聊天消息（核心方法）
     *
     * @param chatMessageDTO 聊天消息数据传输对象
     * @return 创建成功的聊天消息实体（包含自增ID）
     */
    private ChatMessage doCreateChatMessage(ChatMessageDTO chatMessageDTO) {
        ChatMessage chatMessage = null;
        try {
            // 将DTO转换为实体对象
            chatMessage = chatMessageConverter.toEntity(chatMessageDTO);
            // 设置创建时间戳
            chatMessage.setCreatedAt(LocalDateTime.now());
            // 设置更新时间戳
            chatMessage.setUpdatedAt(LocalDateTime.now());

            // 插入数据库并获取影响行数，用户发送的信息会被设置为user角色
            int result = chatMessageMapper.insert(chatMessage);
            // 检查插入是否成功
            if (result <= 0) {
                throw new RuntimeException("创建聊天消息失败");
            }
            return chatMessage;
        } catch (JsonProcessingException e) {
            // 序列化异常包装为业务异常抛出
            throw new BizException("创建聊天消息时发生序列化错误：" + e.getMessage());
        }
    }
}