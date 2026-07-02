package com.kaer.context.compact;

import com.kaer.context.token.TokenCounter;
import com.kaer.context.truncator.ContextTruncator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L3 — 单条工具响应超限截断（最便宜的规则裁剪）。
 *
 * <p>委托现有 {@link ContextTruncator#truncateToolResponses} 处理。
 * 该组件内部走 {@code ToolResponseCacheInterceptor}（Redis 缓存 + CacheId 后缀）
 * 或降级为硬截断。
 *
 * <p>在 Pipeline 中最先执行：截断超大单条工具响应，避免一条结果吃掉全部预算。
 */
@Slf4j
@Component
public class L3SingleToolResponseGate implements ContextCompactor {

    private final ContextTruncator contextTruncator;
    private final TokenCounter tokenCounter;

    public L3SingleToolResponseGate(ContextTruncator contextTruncator, TokenCounter tokenCounter) {
        this.contextTruncator = contextTruncator;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public CompactResult compact(List<Message> messages, CompactorContext context) {
        int beforeTokens = tokenCounter.estimateTokens(messages);

        List<Message> truncated = contextTruncator.truncateToolResponses(
                messages,
                context.getMaxToolResponseTokens(),
                context.getSessionId()
        );

        int afterTokens = tokenCounter.estimateTokens(truncated);
        boolean satisfied = afterTokens <= context.getTokenBudget();

        log.info("[L3] 工具响应截断: tokens {}→{} (Δ{}), budget={}, satisfied={}, "
                        + "msgs={}",
                beforeTokens, afterTokens, beforeTokens - afterTokens,
                context.getTokenBudget(), satisfied, messages.size());

        if (satisfied) {
            return CompactResult.pass(truncated, "L3");
        }
        return CompactResult.continue_(truncated, "L3");
    }
}
