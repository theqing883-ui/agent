package com.kaer.context.compact;

import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 压缩结果 —— 携带压缩后的消息列表及元信息。
 *
 * <p>由各级压缩器返回，供 {@link CompactorPipeline} 判断是否继续下一级。
 */
@Getter
@Builder
public class CompactResult {

    /** 压缩后的消息列表 */
    private final List<Message> messages;

    /** Token 预算是否已满足 */
    private final boolean budgetSatisfied;

    /** 聊天记忆是否被替换（L4 会执行 destructure replace） */
    private final boolean memoryReplaced;

    /** 触发操作标识，用于日志/指标：NONE | L3 | L2 | L1 | L4 */
    private final String action;

    // ===== Builder 便捷方法 =====

    /** 创建带有新 action 值的副本 */
    public CompactResult withAction(String newAction) {
        return CompactResult.builder()
                .messages(this.messages)
                .budgetSatisfied(this.budgetSatisfied)
                .memoryReplaced(this.memoryReplaced)
                .action(newAction)
                .build();
    }

    /** 创建带有新 budgetSatisfied 值的副本 */
    public CompactResult withBudgetSatisfied(boolean satisfied) {
        return CompactResult.builder()
                .messages(this.messages)
                .budgetSatisfied(satisfied)
                .memoryReplaced(this.memoryReplaced)
                .action(this.action)
                .build();
    }

    /** 快捷工厂：通过（预算满足） */
    public static CompactResult pass(List<Message> messages, String action) {
        return CompactResult.builder()
                .messages(messages)
                .budgetSatisfied(true)
                .memoryReplaced(false)
                .action(action)
                .build();
    }

    /** 快捷工厂：未通过（需要继续下级） */
    public static CompactResult continue_(List<Message> messages, String action) {
        return CompactResult.builder()
                .messages(messages)
                .budgetSatisfied(false)
                .memoryReplaced(false)
                .action(action)
                .build();
    }

    /** 快捷工厂：通过并替换了 memory（L4） */
    public static CompactResult replaced(List<Message> messages) {
        return CompactResult.builder()
                .messages(messages)
                .budgetSatisfied(true)
                .memoryReplaced(true)
                .action("L4")
                .build();
    }
}
