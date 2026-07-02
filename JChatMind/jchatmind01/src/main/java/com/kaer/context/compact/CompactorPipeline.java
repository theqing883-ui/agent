package com.kaer.context.compact;

import com.kaer.context.token.TokenCounter;
import com.kaer.service.impl.MemoryNoteStoreServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩流水线 —— 按 L3 → L2 → L1 → L4 顺序执行四级降级。
 *
 * <h3>执行规则</h3>
 * <ol>
 *   <li>每级完成后检查 Token 预算是否满足</li>
 *   <li>满足 → 短路退出，不执行后续级别</li>
 *   <li>不满足 → 继续下一级</li>
 *   <li>L4 作为最终兜底，触发后必定满足预算（直接替换 chatMemory）</li>
 * </ol>
 *
 * <h3>插入点</h3>
 * <p>由 {@code ContextWindowManager.buildContextWindow()} 调用，
 * 替代原有的 {@code chatMemory.getByTokenBudget()}。
 */
@Slf4j
@Component
public class CompactorPipeline {
    private final MemoryNoteStoreServiceImpl memoryNoteStoreServiceImpl;
    private final L3SingleToolResponseGate l3;
    private final L2HistoricalToolResultCleanup l2;
    private final L1SlidingWindowTrim l1;
    private final L4FullSummaryFallback l4;
    private final TokenCounter tokenCounter;

    public CompactorPipeline(L3SingleToolResponseGate l3,
                             L2HistoricalToolResultCleanup l2,
                             L1SlidingWindowTrim l1,
                             L4FullSummaryFallback l4,
                             TokenCounter tokenCounter,
                             MemoryNoteStoreServiceImpl memoryNoteStoreServiceImpl
    ) {
        this.l3 = l3;
        this.l2 = l2;
        this.l1 = l1;
        this.l4 = l4;
        this.tokenCounter = tokenCounter;
        this.memoryNoteStoreServiceImpl = memoryNoteStoreServiceImpl;

    }

    /**
     * 执行完整的四级降级流水线。
     *
     * @param messages 全量消息列表（通常来自 chatMemory.getAll()）
     * @param context  压缩上下文
     * @return 压缩结果
     */
    public CompactResult compact(List<Message> messages, CompactorContext context) {
        int initialTokens = tokenCounter.estimateTokens(messages);
        log.info("[CompactorPipeline] 开始压缩: msgs={}, tokens={}, budget={}",
                messages.size(), initialTokens, context.getTokenBudget());

        CompactResult result = CompactResult.builder()
                .messages(new ArrayList<>(messages))
                .budgetSatisfied(false)
                .memoryReplaced(false)
                .action("原始消息")
                .build();

        // -- 添加记忆笔记 --
        List<String> memoryNotes = memoryNoteStoreServiceImpl.getNotes(context.getSessionId());
        for (String note : memoryNotes) {
            result.getMessages().add(new SystemMessage("[记忆笔记]\n" + note));
        }

        // —— L3: 单条工具响应超限截断 ——
        result = l3.compact(result.getMessages(), context);
        if (result.isBudgetSatisfied()) {
            log.info("[CompactorPipeline] L3 满足预算，短路退出");
            return result;
        }

        // —— L2: 历史工具结果冗余清理 ——
        result = l2.compact(result.getMessages(), context);
        if (result.isBudgetSatisfied()) {
            log.info("[CompactorPipeline] L2 满足预算，短路退出");
            return result;
        }

        // —— L1: 头尾保留滑动窗口 ——
        result = l1.compact(result.getMessages(), context);
        if (result.isBudgetSatisfied()) {
            log.info("[CompactorPipeline] L1 满足预算，短路退出");
            return result;
        }


        // —— L4: LLM 全量摘要兜底 ——
        result = l4.compact(result.getMessages(), context);
        // L4 总是返回 budgetSatisfied=true（如果 LLM 调用成功），或原始消息（失败时）
        if (result.isBudgetSatisfied()) {
            log.info("[CompactorPipeline] L4 兜底完成, memoryReplaced={}",
                    result.isMemoryReplaced());
        } else {
            log.warn("[CompactorPipeline] 所有级别均未满足预算！tokens={}, budget={}",
                    tokenCounter.estimateTokens(result.getMessages()),
                    context.getTokenBudget());
        }

        return result;
    }
}
