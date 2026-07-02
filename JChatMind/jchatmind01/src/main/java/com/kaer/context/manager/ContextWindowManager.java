package com.kaer.context.manager;

import com.kaer.agent.ConstantPrompt;
import com.kaer.context.budget.BudgetAllocator;
import com.kaer.context.compact.CompactResult;
import com.kaer.context.compact.CompactorContext;
import com.kaer.context.compact.CompactorPipeline;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.model.BudgetAllocation;
import com.kaer.context.model.ContextWindow;
import com.kaer.context.model.TokenUsage;
import com.kaer.context.token.TokenCounter;
import com.kaer.model.dto.AgentDTO;
import com.kaer.service.impl.MemoryNoteStoreServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ContextWindowManager {

    private final TokenCounter tokenCounter;
    private final BudgetAllocator budgetAllocator;
    private final MemoryNoteStoreServiceImpl memoryNoteStoreServiceImpl;


    private final TokenAwareChatMemory chatMemory;
    private final ChatClient chatClient;
    private final CompactorPipeline compactorPipeline;

    public ContextWindowManager(
            MemoryNoteStoreServiceImpl memoryNoteStoreServiceImpl,
            TokenCounter tokenCounter,
            BudgetAllocator budgetAllocator,
            TokenAwareChatMemory chatMemory,
            ChatClient chatClient,
            CompactorPipeline compactorPipeline
    ) {
        this.tokenCounter = tokenCounter;
        this.budgetAllocator = budgetAllocator;
        this.memoryNoteStoreServiceImpl = memoryNoteStoreServiceImpl;
        this.chatMemory = chatMemory;
        this.chatClient = chatClient;
        this.compactorPipeline = compactorPipeline;
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

        // 获取会话的所有消息历史
        List<Message> allMessages = chatMemory.getAll(sessionId);

        // 1. 分配预算：根据总预算和各组件需求，计算各部分可用 token
        BudgetAllocation allocation = budgetAllocator.allocate(
                totalBudget, systemPrompt, toolCallbacks, allMessages, config);
//        BudgetAllocation allocation = new BudgetAllocation(2000, 1400, 2000, 10000);

        // 2. 四级降级压缩流水线（替代原先的 getByTokenBudget）
        //    L3: 工具响应截断 → L2: 历史工具清理 → L1: 滑动窗口 → L4: LLM 摘要
        CompactorContext compactorCtx = CompactorContext.builder()
                .sessionId(sessionId)
                .tokenBudget(allocation.messagesBudget())
                .config(config)
                .tokenCounter(tokenCounter)
                .chatMemory(chatMemory)
                .cheapChatClient(chatClient)  // null = 使用主模型（由 L4 配置决定）
                .build();

        CompactResult compactResult = compactorPipeline.compact(allMessages, compactorCtx);
        List<Message> finalMessages = compactResult.getMessages();

        // 3. 获取记忆笔记：从持久化存储中读取该会话的所有记忆笔记
        List<String> memoryNotes = memoryNoteStoreServiceImpl.getNotes(sessionId);


        // 7. 计算 token 用量统计
        int msgTokens = tokenCounter.estimateTokens(finalMessages);           // 消息 token 数
        int sysTokens = tokenCounter.estimateTokens(systemPrompt);      // 系统提示 token 数
        int noteTokens = memoryNotes.stream().mapToInt(tokenCounter::estimateTokens).sum(); // 笔记 token 数

        // 构建 TokenUsage 对象记录详细用量
        TokenUsage usage = new TokenUsage(
                sysTokens + allocation.toolDefinitionsBudget() + msgTokens + noteTokens,
                sysTokens,
                allocation.toolDefinitionsBudget(),
                msgTokens,
                noteTokens
        );

        // 记录上下文窗口构建日志
        log.info("Context window: total={}/{}K, msg={}, notes={}t",
                usage.totalTokens(), totalBudget / 1000,
                finalMessages.size(), noteTokens);

        return new ContextWindow(finalMessages, memoryNotes, usage);
    }


    /**
     * 检查是否应该生成记忆笔记。
     */
    public boolean shouldGenerateNote(String sessionId, int intervalTurns) {
        return memoryNoteStoreServiceImpl.shouldGenerateNote(sessionId, intervalTurns);
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
            for (int i = allMessages.size() - 1; i >= 0 && count < 5; i--) {
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
                    .system(ConstantPrompt.MEMORY_NOTE_SYSTEM_PROMPT)
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


}