package com.kaer.context.compact;

import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.token.TokenCounter;
import com.kaer.model.dto.AgentDTO;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 压缩器上下文 —— 携带压缩流程所需的所有配置和服务依赖。
 *
 * <p>由 {@code ContextWindowManager} 在每次构建上下文窗口时创建，
 * 传入 {@link CompactorPipeline#compact} 方法。
 */
@Getter
@Builder
public class CompactorContext {

    /** 会话 ID */
    private final String sessionId;

    /** 消息部分的 Token 预算（来自 BudgetAllocator） */
    private final int tokenBudget;

    /** Agent 的聊天配置选项 */
    private final AgentDTO.ChatOptions config;

    /** Token 计数器 */
    private final TokenCounter tokenCounter;

    /** 聊天记忆存储（L4 需要 destructure replace） */
    private final TokenAwareChatMemory chatMemory;

    /** L4 摘要用廉价 ChatClient，{@code null} 时使用主模型 */
    private final ChatClient cheapChatClient;

    // ===== 从 config 提取的便捷 getter（带全局默认值兜底） =====

    /** L1 滑动窗口：保留开头 K 条消息 */
    public int getKeepFirstK() {
        Integer v = config.getCompactL1KeepFirstK();
        return (v != null && v >= 0) ? v : 5;
    }

    /** L1 滑动窗口：保留末尾 N 条消息 */
    public int getKeepLastN() {
        Integer v = config.getCompactL1KeepLastN();
        return (v != null && v >= 0) ? v : 35;
    }

    /** L2 历史工具结果：保留最近 M 条 */
    public int getMaxKeptResults() {
        Integer v = config.getCompactL2MaxKeptResults();
        return (v != null && v >= 0) ? v : 5;
    }

    /** L4 是否启用 */
    public boolean isL4Enabled() {
        Boolean v = config.getCompactL4Enabled();
        return v == null || v; // 默认 true
    }

    /** L4 缩略模型名称 */
    public String getL4CheapModel() {
        return config.getCompactL4CheapModel();
    }

    /** L3 工具响应截断阈值 */
    public int getMaxToolResponseTokens() {
        Integer v = config.getMaxToolResponseTokens();
        return (v != null && v > 0) ? v : 4000;
    }
}
