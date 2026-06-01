package com.kaer.context.budget;

import com.kaer.context.model.BudgetAllocation;
import com.kaer.context.token.TokenCounter;
import com.kaer.model.dto.AgentDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 预算分配器，按分层策略分配上下文窗口的 token 预算。
 */
@Slf4j
@Component
@AllArgsConstructor
public class BudgetAllocator {

    private  final TokenCounter tokenCounter;

    /**
     * 根据总 token 预算，为上下文窗口的各组件分配 token 额度。
     *
     * <p>分配策略：
     * 1. 系统提示：取配置预留值与实际计算值的较大者，确保系统提示完整
     * 2. 工具定义：取实际估算值与配置预留值的较小者，避免过度占用
     * 3. 消息历史：使用剩余预算，但不超过配置的最大消息预算
     *
     * @param totalBudget   总 token 预算（上下文窗口最大容量）
     * @param systemPrompt  系统提示词内容
     * @param toolCallbacks 可用工具回调列表
     * @param messages      消息历史列表（用于估算，但实际消息预算不受消息数量直接影响）
     * @param config        聊天配置选项
     * @return 预算分配结果，包含各组件的 token 额度
     */
    public BudgetAllocation allocate(
            int totalBudget,
            String systemPrompt,
            List<ToolCallback> toolCallbacks,
            List<Message> messages,
            AgentDTO.ChatOptions config
    ) {
        // 获取配置的系统提示预留 token，默认 2000
        int sysReserve = config.getSystemPromptReserveTokens() != null
                ? config.getSystemPromptReserveTokens() : 2000;
        // 获取配置的工具定义预留 token，默认 3000
        int toolReserve = config.getToolDefinitionsReserveTokens() != null
                ? config.getToolDefinitionsReserveTokens() : 3000;
        // 获取配置的最近消息最大预算，默认 60000
        int recentMessagesBudget = config.getRecentMessagesTokenBudget() != null
                ? config.getRecentMessagesTokenBudget() : 60000;

        // 估算系统提示实际需要的 token 数
        int actualSysTokens = tokenCounter.estimateTokens(systemPrompt);
        // 系统提示预算：取预留值与实际值的较大者，确保系统提示不被截断
        int sysBudget = Math.max(sysReserve, actualSysTokens);

        // 估算工具定义需要的 token 数
        int toolTokens = estimateToolDefinitions(toolCallbacks);
        // 必须采用实际值！确保账目完全对齐，防止低估导致上下文溢出
        int toolBudget = toolTokens;

        // 进阶预留：如果实际工具占用了太多空间，进行预警
        if (config.getToolDefinitionsReserveTokens() != null && toolBudget > config.getToolDefinitionsReserveTokens()) {
            log.warn("当前加载的工具定义体积({}t)已超过预期设定的值({}t)，将自动压缩历史消息的可用空间。",
                    toolTokens, config.getToolDefinitionsReserveTokens());
        }

        // 计算固定部分（系统提示 + 工具定义）总占用
        int usedForFixedParts = sysBudget + toolBudget;
        // 剩余可用于消息的预算
        int remaining = totalBudget - usedForFixedParts;

        // 消息预算：不超过配置的最大消息预算，且不小于 0
        int messageBudget = Math.min(recentMessagesBudget, Math.max(0, remaining));

        return new BudgetAllocation(sysBudget, toolBudget, messageBudget, totalBudget);
    }

    private int estimateToolDefinitions(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null) {
            return 0;
        }
        int tokens = 0;
        for (ToolCallback tc : toolCallbacks) {
            var def = tc.getToolDefinition();
            tokens += tokenCounter.estimateTokens(def.name());
            tokens += tokenCounter.estimateTokens(def.description());
        }
        return tokens;
    }
}