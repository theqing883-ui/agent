package com.kaer.context.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.agent.SystemPrompt;
import com.kaer.context.budget.BudgetAllocator;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.model.BudgetAllocation;
import com.kaer.context.model.ContextWindow;
import com.kaer.context.model.TokenUsage;
import com.kaer.context.token.TokenCounter;
import com.kaer.context.truncator.ContextTruncator;
import com.kaer.converter.ChatMessageConverter;
import com.kaer.model.dto.AgentDTO;
import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.entity.ChatMessage;
import com.kaer.service.ChatMessageFacadeService;
import com.kaer.service.impl.ChatSummaryServiceImpl;
import com.kaer.service.impl.MemoryNoteStoreServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ContextWindowManager {

    private final TokenCounter tokenCounter;
    private final BudgetAllocator budgetAllocator;
    private final ContextTruncator contextTruncator;
    private final MemoryNoteStoreServiceImpl memoryNoteStoreServiceImpl;
    private final ChatSummaryServiceImpl chatSummaryServiceImpl;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final ChatMessageConverter chatMessageConverter;
    private final TokenAwareChatMemory chatMemory;
    private final ChatClient chatClient;

    public ContextWindowManager(MemoryNoteStoreServiceImpl memoryNoteStoreServiceImpl,
                                ChatSummaryServiceImpl chatSummaryServiceImpl,
                                TokenCounter tokenCounter,
                                BudgetAllocator budgetAllocator,
                                ContextTruncator contextTruncator,
                                ChatMessageFacadeService chatMessageFacadeService,
                                ChatMessageConverter chatMessageConverter,
                                TokenAwareChatMemory chatMemory,
                                ChatClient chatClient
    ) {
        this.tokenCounter = tokenCounter;
        this.budgetAllocator = budgetAllocator;
        this.contextTruncator = contextTruncator;
        this.memoryNoteStoreServiceImpl = memoryNoteStoreServiceImpl;
        this.chatSummaryServiceImpl = chatSummaryServiceImpl;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;
        this.chatMemory = chatMemory;
        this.chatClient = chatClient;
    }

    /**
     * 构建对话上下文窗口，整合消息历史、记忆笔记和摘要，确保在模型 token 限制内。
     *
     * <p>核心功能：
     * - 根据配置分配 token 预算给各个组成部分（系统提示、工具定义、消息、摘要、笔记）
     * - 智能截断消息历史，优先保留最新消息
     * - 自动生成对话摘要以节省 token 空间
     * - 整合持久化记忆笔记到上下文
     *
     * @param sessionId     会话唯一标识
     * @param chatMemory    支持 token 感知的聊天内存
     * @param toolCallbacks 可用工具回调列表
     * @param systemPrompt  系统提示词
     * @param config        聊天配置选项
     * @return 构建完成的 ContextWindow 对象，包含最终消息列表和 token 使用统计
     */
    public ContextWindow buildContextWindow(
            String sessionId,
            TokenAwareChatMemory chatMemory,
            List<ToolCallback> toolCallbacks,
            String systemPrompt,
            AgentDTO.ChatOptions config
    ) {
        // 获取总 token 预算，默认 128K
        int totalBudget = config.getMaxContextTokens() != null
                ? config.getMaxContextTokens() : 128000;
        // 获取摘要功能开关，默认开启
        boolean summarizationEnabled = config.getSummarizationEnabled() != null
                ? config.getSummarizationEnabled() : true;

        // 获取会话的所有消息历史
        List<Message> allMessages = chatMemory.getAll(sessionId);

        // 1. 分配预算：根据总预算和各组件需求，计算各部分可用 token
        BudgetAllocation allocation = budgetAllocator.allocate(
                totalBudget, systemPrompt, toolCallbacks, allMessages, config);

        // 2. 按预算截断消息列表：从最新消息开始保留，直到用完消息预算
        List<Message> selected = chatMemory.getByTokenBudget(sessionId, allocation.messagesBudget());

        // 3. 截断工具响应：限制单个工具响应的最大 token 数，默认 4000
        int maxToolResp = config.getMaxToolResponseTokens() != null
                ? config.getMaxToolResponseTokens() : 4000;
        selected = contextTruncator.truncateToolResponses(selected, maxToolResp);

        // 4. 获取记忆笔记：从持久化存储中读取该会话的所有记忆笔记
        List<String> memoryNotes = memoryNoteStoreServiceImpl.getNotes(sessionId);

        // 5. 对话摘要处理：如果开启摘要，找出被驱逐的消息并增量更新数据库摘要
        String conversationSummary = null;
        if (summarizationEnabled) {
            // 计算被驱逐的非系统消息数量
            long nonSystemInAll = allMessages.stream()
                    .filter(m -> !(m instanceof SystemMessage)).count();
            long nonSystemInSelected = selected.stream()
                    .filter(m -> !(m instanceof SystemMessage)).count();
            int evictedCount = (int) (nonSystemInAll - nonSystemInSelected);
            if (evictedCount > 0) {
                // 从数据库中获取被驱逐的消息历史
                int length = allMessages.size();
                List<ChatMessageDTO> recentlyChatMessageDTOs = chatMessageFacadeService.getChatMessagesBySessionIdRecently(sessionId, length);
                List<ChatMessage> recentlyChatMessages = new ArrayList<>();
                try {
                    for (ChatMessageDTO chatMessageDTO : recentlyChatMessageDTOs) {
                        recentlyChatMessages.add(chatMessageConverter.toEntity(chatMessageDTO));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                int count = Math.min(evictedCount, recentlyChatMessages.size());
                List<ChatMessage> evictedChatMessages = recentlyChatMessages.subList(0, count);
                conversationSummary = chatSummaryServiceImpl.updateSessionSummary(sessionId, evictedChatMessages);
            }
        }

        // 6. 构建最终消息列表：按优先级排列
        List<Message> finalMessages = new ArrayList<>();
        // 第一优先级：静态系统指令（Agent 人设 + thinkPrompt 调度规则）,think()方法中调用模型时.system()方法发送
        // 添加对话历史摘要
        if (conversationSummary != null && !conversationSummary.isEmpty()) {
            finalMessages.add(new SystemMessage(
                    "[对话历史摘要]\n" + conversationSummary));
        }
        // 添加记忆笔记
        for (String note : memoryNotes) {
            finalMessages.add(new SystemMessage("[记忆笔记]\n" + note));
        }
        // 添加选中的消息历史
        finalMessages.addAll(selected);

        // 7. 计算 token 用量统计
        int msgTokens = tokenCounter.estimateTokens(selected);           // 消息 token 数
        int sysTokens = tokenCounter.estimateTokens(systemPrompt);      // 系统提示 token 数
        int summaryTokens = tokenCounter.estimateTokens(conversationSummary); // 摘要 token 数
        int noteTokens = memoryNotes.stream().mapToInt(tokenCounter::estimateTokens).sum(); // 笔记 token 数

        // 构建 TokenUsage 对象记录详细用量
        TokenUsage usage = new TokenUsage(
                sysTokens + allocation.toolDefinitionsBudget() + msgTokens + noteTokens + summaryTokens,
                sysTokens,
                allocation.toolDefinitionsBudget(),
                msgTokens,
                summaryTokens,
                noteTokens
        );

        // 记录上下文窗口构建日志
        log.info("Context window: total={}/{}K, msg={}, summary={}t, notes={}t",
                usage.totalTokens(), totalBudget / 1000,
                selected.size(), summaryTokens, noteTokens);

        return new ContextWindow(finalMessages, conversationSummary, memoryNotes, usage);
    }


    /**
     * 检查是否应该生成记忆笔记。
     */
    public boolean shouldGenerateNote(String sessionId, int intervalTurns) {
        return memoryNoteStoreServiceImpl.shouldGenerateNote(sessionId, intervalTurns);
    }

    /**
     * 添加一条记忆笔记（持久化 + 缓存）。
     */
    public void addMemoryNote(String sessionId, String content) {
        memoryNoteStoreServiceImpl.addNote(sessionId, content);
    }

    /**
     * 基于最近的对话生成一条记忆笔记。
     */
    public void generateMemoryNote(String sessionId) {
        try {
            List<Message> allMessages = chatMemory.getAll(sessionId);
            // 取最近 4 条非 SystemMessage 的内容作为摘要材料
            int count = 0;
            StringBuilder recentContext = new StringBuilder();
            for (int i = allMessages.size() - 1; i >= 0 && count < 4; i--) {
                Message msg = allMessages.get(i);
                if (msg instanceof SystemMessage) continue;
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    recentContext.insert(0, "[" + msg.getMessageType() + "]: "
                            + (text.length() > 300 ? text.substring(0, 300) : text) + "\n");
                    count++;
                }
            }
            if (recentContext.isEmpty()) return;

            String userPrompt = "请提取以下对话片段的记忆笔记：%s \n".formatted(recentContext.toString());

            String note = chatClient.prompt()
                    .system(SystemPrompt.MEMORY_NOTE_SYSTEM_PROMPT)
                    .user(userPrompt)
                    // 强制模型只输出文本，忽略绑定的工具
                    .options(DefaultToolCallingChatOptions.builder()
                            .internalToolExecutionEnabled(false) // 禁用工具调用
                            .temperature(0.3)                    // 低温度保证稳定输出
                            .build())
                    .call()
                    .content();

            if (note != null && !note.isBlank()) {
                memoryNoteStoreServiceImpl.addNote(sessionId, note.trim());
                log.info("记忆笔记已生成: {}", note.trim());
            }
        } catch (Exception e) {
            log.warn("生成记忆笔记失败", e);
        }
    }

    /**
     * 清除会话上下文（会话结束时调用）。
     * 摘要已持久化到数据库，无需手动清除。
     */
    public void clearSession(String sessionId) {
        // 摘要已持久化到 chat_summary 表，无需清除
    }
}