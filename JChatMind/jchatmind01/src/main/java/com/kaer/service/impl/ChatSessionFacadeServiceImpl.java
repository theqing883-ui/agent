package com.kaer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.converter.ChatSessionConverter;
import com.kaer.exception.BizException;
import com.kaer.mapper.ChatSessionMapper;
import com.kaer.model.dto.ChatSessionDTO;
import com.kaer.model.entity.ChatSession;
import com.kaer.model.request.CreateChatSessionRequest;
import com.kaer.model.request.UpdateChatSessionRequest;
import com.kaer.model.response.CreateChatSessionResponse;
import com.kaer.model.response.GetChatSessionResponse;
import com.kaer.model.response.GetChatSessionsResponse;
import com.kaer.model.vo.ChatSessionVO;
import com.kaer.service.ChatSessionFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatSessionFacadeServiceImpl implements ChatSessionFacadeService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionConverter chatSessionConverter;

    /**
     * 获取所有聊天会话列表
     * <p>查询数据库中所有的聊天会话记录，转换为视图对象后返回</p>
     *
     * @return GetChatSessionsResponse 包含所有会话列表的响应对象
     * @throws RuntimeException 当序列化异常时抛出运行时异常
     */
    @Override
    public GetChatSessionsResponse getChatSessions() {
        // 查询所有会话实体
        List<ChatSession> chatSessions = chatSessionMapper.selectAll();
        ArrayList<ChatSessionVO> result = new ArrayList<>();

        // 遍历转换为视图对象
        for (ChatSession chatSession : chatSessions) {
            try {
                result.add(chatSessionConverter.toVO(chatSession));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        // 构建并返回响应
        return GetChatSessionsResponse.builder().chatSessions(result.toArray(new ChatSessionVO[0])).build();
    }

    // 查询单个会话记录
    @Override
    public GetChatSessionResponse getChatSession(String chatSessionId) {
        ChatSession chatSession = chatSessionMapper.selectById(chatSessionId);
        if (chatSession != null) {
            try {
                ChatSessionVO vo = chatSessionConverter.toVO(chatSession);
                return GetChatSessionResponse.builder().chatSession(vo).build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new BizException("会话不存在" + chatSessionId);
        }


    }


    // 根据AgentId查询对话
    @Override
    public GetChatSessionsResponse getChatSessionsByAgentId(String agentId) {
        List<ChatSession> chatSessions = chatSessionMapper.selectByAgentId(agentId);
        if (chatSessions == null) {
            throw new BizException("智能体不存在" + agentId);
        }
        ArrayList<ChatSessionVO> result = new ArrayList<>();
        for (ChatSession chatSession : chatSessions) {
            try {
                result.add(chatSessionConverter.toVO(chatSession));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetChatSessionsResponse.builder()
                .chatSessions(result.toArray(new ChatSessionVO[0]))
                .build();
    }

    /**
     * 创建聊天会话
     * <p>根据请求参数创建一个新的聊天会话，执行请求转换、实体构建、数据持久化等操作</p>
     *
     * @param request 创建会话请求，包含会话相关配置信息
     * @return CreateChatSessionResponse 创建响应，包含自动生成的会话唯一标识
     * @throws BizException 当数据库插入失败或序列化异常时抛出业务异常
     */
    @Override
    public CreateChatSessionResponse createChatSession(CreateChatSessionRequest request) {
        try {
            // 将请求对象转换为数据传输对象
            ChatSessionDTO chatSessionDTO = chatSessionConverter.toDTO(request);
            // 将DTO转换为数据库实体对象
            ChatSession chatSession = chatSessionConverter.toEntity(chatSessionDTO);

            // 设置创建时间和更新时间为当前时间
            LocalDateTime now = LocalDateTime.now();
            chatSession.setCreatedAt(now);
            chatSession.setUpdatedAt(now);

            // 插入数据库，返回影响的行数
            int result = chatSessionMapper.insert(chatSession);
            if (result <= 0) {
                throw new BizException("创建会话失败");
            }

            // 返回生成的会话ID
            return CreateChatSessionResponse.builder().chatSessionId(chatSession.getId()).build();
        } catch (JsonProcessingException e) {
            throw new BizException("创建会话时发生序列化错误：" + e.getMessage());
        }
    }

    // 删除聊天会话
    @Override
    public void deleteChatSession(String chatSessionId) {
        ChatSession chatSession = chatSessionMapper.selectById(chatSessionId);
        if (chatSession == null) {
            throw new BizException("会话不存在" + chatSessionId);
        }
        int result = chatSessionMapper.deleteById(chatSessionId);
        if (result <= 0) {
            throw new BizException("删除聊天会话失败");
        }
    }

    /**
     * 更新聊天会话
     * <p>
     * 根据会话ID更新聊天会话信息，支持更新标题等可修改字段。
     * 执行流程：查询会话 -> 转换DTO -> 更新字段 -> 转换实体 -> 持久化。
     *
     * @param chatSessionId 聊天会话唯一标识
     * @param request       更新请求，包含要更新的字段（如标题）
     * @throws BizException 当会话不存在或更新失败时抛出
     */
    @Override
    public void updateChatSession(String chatSessionId, UpdateChatSessionRequest request) {
        try {
            // 1. 根据会话ID查询现有会话
            ChatSession existingChatSession = chatSessionMapper.selectById(chatSessionId);
            // 2. 校验会话是否存在
            if (existingChatSession == null) {
                throw new BizException("聊天会话不存在" + chatSessionId);
            }
            // 3. 将实体转换为DTO进行字段更新
            ChatSessionDTO chatSessionDTO = chatSessionConverter.toDTO(existingChatSession);
            // 4. 根据请求更新DTO字段（如标题）
            chatSessionConverter.updateDTOFromRequest(chatSessionDTO, request);
            // 5. 将更新后的DTO转换回实体
            ChatSession updateChatSession = chatSessionConverter.toEntity(chatSessionDTO);
            // 6. 设置更新时间戳，保留原有创建时间、ID和AgentId
            updateChatSession.setUpdatedAt(LocalDateTime.now());
            updateChatSession.setCreatedAt(existingChatSession.getCreatedAt());
            updateChatSession.setId(existingChatSession.getId());
            updateChatSession.setAgentId(existingChatSession.getAgentId());
            // 7. 执行数据库更新
            int result = chatSessionMapper.updateById(updateChatSession);
            // 8. 校验更新结果
            if (result <= 0) {
                throw new BizException("更新聊天会话失败");
            }
        } catch (Exception e) {
            // 9. 异常包装处理
            throw new RuntimeException(e);
        }
    }
}