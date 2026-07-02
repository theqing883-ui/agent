package com.kaer.context.compact;

import com.kaer.agent.ConstantPrompt;
import com.kaer.context.token.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * L4 — LLM 全量摘要兜底（最昂贵的终极压缩）。
 *
 * <p><b>触发条件：</b>L3 + L2 + L1 全部执行后，Token 仍超出预算。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>将当前消息列表格式化为 role:content 文本（每条截断 500 字符）</li>
 *   <li>调用廉价 LLM（或主模型）生成结构化摘要，提取 5 大要素：
 *       <ul>
 *         <li>当前目标</li>
 *         <li>关键决定</li>
 *         <li>变更文件</li>
 *         <li>待办事项</li>
 *         <li>用户约束</li>
 *       </ul></li>
 *   <li>找到原始消息列表中最后一条 UserMessage</li>
 *   <li>调用 {@code chatMemory.replace()} 清空历史，注入摘要 + 最后一条用户消息</li>
 *   <li>返回新消息列表（budgetSatisfied=true, memoryReplaced=true）</li>
 * </ol>
 *
 * <h3>降级保证</h3>
 * <p>LLM 调用失败时，保持原始消息不变，返回 budgetSatisfied=false，
 * 由上层 {@code ErrorRecoveryEngine} 的 L4 处理 API 400 错误。
 */
@Slf4j
@Component
public class L4FullSummaryFallback implements ContextCompactor {
    private final TokenCounter tokenCounter;

    public L4FullSummaryFallback(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    @Override
    public CompactResult compact(List<Message> messages, CompactorContext context) {
        if (!context.isL4Enabled()) {
            log.info("[L4] 未启用，跳过");
            return CompactResult.continue_(messages, "L4");
        }

        // 如果已经在预算内，不触发
        int currentTokens = tokenCounter.estimateTokens(messages);
        if (currentTokens <= context.getTokenBudget()) {
            log.debug("[L4] 已在预算内({}≤{})，跳过", currentTokens, context.getTokenBudget());
            return CompactResult.pass(messages, "L4");
        }

        log.info("[L4] 触发全量摘要: 当前 tokens={} > budget={}, 消息数={}",
                currentTokens, context.getTokenBudget(), messages.size());

        // 步1: 格式化消息历史为文本
        String formattedHistory = formatMessagesForSummary(messages);

        // 步2: 找到最后一条 UserMessage
        Message lastUserMsg = findLastUserMessage(messages);

        // 步3: 调用 LLM 生成摘要
        ChatClient client = context.getCheapChatClient();
        String summary;
        try {
            summary = generateSummary(client, formattedHistory);
        } catch (Exception e) {
            log.error("[L4] LLM 摘要生成失败，保持原始消息不变", e);
            // 降级：返回原始消息，让 ErrorRecoveryEngine 处理
            return CompactResult.continue_(messages, "L4");
        }

        if (summary == null || summary.isBlank()) {
            log.warn("[L4] 摘要为空，保持原始消息不变");
            return CompactResult.continue_(messages, "L4");
        }

        // 步4: 构建新消息列表：摘要 SystemMessage + 最后一条 UserMessage
        List<Message> newMessages = new ArrayList<>();
        newMessages.add(new SystemMessage(
                "[上下文压缩摘要 — 之前的对话历史已被 LLM 压缩]\n" + summary));

        if (lastUserMsg != null) {
            newMessages.add(new UserMessage(lastUserMsg.getText()));
        }

        // 步5: 替换 chatMemory（destructive）
        context.getChatMemory().replace(context.getSessionId(), newMessages);

        int afterTokens = tokenCounter.estimateTokens(newMessages);

        log.info("[L4] 全量摘要完成: {} 条消息 → {} 条, tokens {}→{}, summaryLen={}",
                messages.size(), newMessages.size(),
                currentTokens, afterTokens, summary.length());

        return CompactResult.replaced(newMessages);
    }

    // ==================== 私有方法 ====================

    /**
     * 将消息列表格式化为供 LLM 读取的纯文本。
     */
    private String formatMessagesForSummary(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            String role = msg.getMessageType().name();

            sb.append("[").append(role).append("]");

            // SystemMessage / UserMessage / AssistantMessage
            String text = msg.getText();
            sb.append(": ").append(text);


            // ToolResponseMessage
            if (msg instanceof org.springframework.ai.chat.messages.ToolResponseMessage toolResp) {
                for (var resp : toolResp.getResponses()) {
                    sb.append("\n  [tool: ").append(resp.name()).append("]");
                    String respData = resp.responseData();
                    sb.append(": ").append(respData);
                }
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 调用 LLM 生成结构化摘要。
     */
    private String generateSummary(ChatClient client, String formattedHistory) {
        return client.prompt()
                .system(ConstantPrompt.COMPACT_SUMMARY_SYSTEM_PROMPT)
                .user("请分析以下对话历史，提取结构化摘要：\n\n" + formattedHistory)
                .options(DefaultToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(false)  // 禁用工具调用
                        .temperature(0.3)                     // 低温度保证稳定
                        .build())
                .call()
                .content();
    }


    /**
     * 找到消息列表中的最后一条 UserMessage。
     */
    private Message findLastUserMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return messages.get(i);
            }
        }
        return null;
    }
}
