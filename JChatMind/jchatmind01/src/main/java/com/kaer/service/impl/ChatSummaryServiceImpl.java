package com.kaer.service.impl;

import com.kaer.agent.ConstantPrompt;
import com.kaer.mapper.ChatSummaryMapper;
import com.kaer.model.entity.ChatMessage;
import com.kaer.model.entity.ChatSummary;
import com.kaer.service.ChatSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话摘要服务，负责生成和更新会话的增量摘要。
 *
 * <p>核心功能：
 * - 根据被驱逐的消息生成增量摘要
 * - 基于数据库 ChatMessage.id 去重，避免重复摘要
 * - 将摘要持久化到数据库
 *
 * <p>设计特点：
 * - 增量更新：只对新消息生成摘要，与已有摘要合并
 * - 去重机制：通过 ChatMessage.id 判断是否已处理过
 * - 容错设计：生成失败时返回已有摘要，保证系统稳定性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummaryServiceImpl implements ChatSummaryService {

    /**
     * 摘要生成的用户提示词模板，包含已有摘要和待追加的新对话片段。
     * 占位符：%s 分别对应已有摘要和新对话文本。
     */
    private static final String SUMMARIZE_USER_TEMPLATE = """
            【已有摘要（如有）】
            %s
            
            【待追加摘要的新对话片段】
            %s
            """;

    /**
     * 摘要数据库访问映射器
     */
    private final ChatSummaryMapper chatSummaryMapper;
    /**
     * AI 聊天客户端，用于调用模型生成摘要
     */
    private final ChatClient chatClient;

    /**
     * 更新会话摘要，将被驱逐的新消息增量合并到已有摘要。
     *
     * <p>执行流程：
     * 1. 检查输入消息是否为空
     * 2. 查询已有摘要和已处理消息 ID
     * 3. 过滤出新消息（通过 ChatMessage.id 去重）
     * 4. 调用 AI 生成增量摘要
     * 5. 持久化更新后的摘要和 ID 列表
     *
     * @param sessionId       会话唯一标识
     * @param evictedMessages 被驱逐出上下文窗口的消息实体列表
     * @return 更新后的摘要内容，若无新消息则返回已有摘要
     */
    @Override
    public String updateSessionSummary(String sessionId, List<ChatMessage> evictedMessages) {
        // 边界处理：空消息列表直接返回
        if (evictedMessages == null || evictedMessages.isEmpty()) {
            return null;
        }
        // 查询已有摘要记录
        ChatSummary existing = chatSummaryMapper.selectBySessionId(sessionId);
        String existingContent = (existing != null && existing.getContent() != null)
                ? existing.getContent() : null;

        // 提取已有消息 ID 列表
        List<String> oldMessageIds = (existing != null && existing.getMessageIds() != null)
                ? existing.getMessageIds() : List.of();

        // 过滤出新消息：直接比对 msg.getId()
        List<ChatMessage> newMessages = evictedMessages.stream()
                .filter(msg -> !oldMessageIds.contains(msg.getId()))
                .toList();

        // 若无新消息，直接返回已有摘要
        if (newMessages.isEmpty()) {
            log.debug("无新消息需要摘要，sessionId={}", sessionId);
            return existingContent;
        }

        // 将新消息格式化为可读文本
        String newMessagesText = newMessages.stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));

        // 处理已有摘要内容
        String existingText = (existingContent != null && !existingContent.isEmpty())
                ? existingContent : "（暂无）";

        // 构建用户提示词
        String userContent = String.format(SUMMARIZE_USER_TEMPLATE, existingText, newMessagesText);

        // 调用 AI 生成摘要
        String result;
        try {
            result = chatClient.prompt()
                    .system(ConstantPrompt.SUMMARIZE_SYSTEM_PROMPT)
                    .user(userContent)
                    .options(DefaultToolCallingChatOptions.builder()
                            .internalToolExecutionEnabled(false) // 禁用工具调用
                            .temperature(0.3)                    // 低温度保证稳定输出
                            .build())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("摘要生成失败，sessionId={}", sessionId, e);
            return existingContent; // 失败时返回已有摘要
        }

        // 处理空结果
        if (result == null || result.isBlank()) {
            log.warn("摘要生成返回为空，sessionId={}", sessionId);
            return existingContent;
        }

        // 限制摘要长度
        String trimmed = result.length() > 2000 ? result.substring(0, 2000) : result;

        // 合并 ID 列表：记录已处理的消息
        List<String> mergedMessageIds = new ArrayList<>(oldMessageIds);
        for (ChatMessage msg : newMessages) {
            mergedMessageIds.add(msg.getId());
        }

        // 构建摘要实体（id 由数据库 gen_random_uuid() 自动生成）
        ChatSummary summary = ChatSummary.builder()
                .sessionId(sessionId)
                .content(trimmed)
                .messageIds(mergedMessageIds)
                .build();

        // 持久化到数据库
        chatSummaryMapper.upsertSummary(summary);

        log.info("摘要已更新: session={}, 新增消息数={}, 累计消息数={}",
                sessionId, newMessages.size(), mergedMessageIds.size());

        return trimmed;
    }

    /**
     * 将 ChatMessage 实体格式化为可读文本，用于摘要生成。
     *
     * <p>格式：[角色]: 消息内容
     * 超过 800 字符的消息会被截断并添加省略号。
     *
     * @param msg ChatMessage 实体对象
     * @return 格式化后的文本字符串
     */
    private String formatMessage(ChatMessage msg) {
        String role = msg.getRole();
        String text = msg.getContent();

        // 空文本处理
        if (text == null || text.isBlank()) {
            return "[" + role + "]: (无文本内容)";
        }

        // 超长文本截断（最多 800 字符）
        if (text.length() > 800) {
            text = text.substring(0, 800) + "...";
        }

        return "[" + role + "]: " + text;
    }
}