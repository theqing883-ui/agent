package com.kaer.context.compact;

import com.kaer.agent.ConstantPrompt;
import com.kaer.context.cache.ToolResultCacheService;
import com.kaer.context.token.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 — 历史工具调用结果冗余清理。
 *
 * <p>扫描整个消息列表中的 {@link ToolResponseMessage}，只保留最近的 M 条在完整状态。
 * 更早的工具结果通过 {@link ToolResultCacheService} 归档到 Redis，原地替换为占位文本，
 * 引导 LLM 通过 {@code read_cache} 工具按需取回。
 *
 * <p>规则：
 * <ol>
 *   <li>从末尾向前数，保留最近 M 条 ToolResponseMessage</li>
 *   <li>超过 M 的更早结果：调用 {@code cacheService.store()} 归档到 Redis</li>
 *   <li>替换 responseData 为 {@link ConstantPrompt#TOOL_RESULT_COMPACTED_SUFFIX}</li>
 *   <li>如果结果数量 ≤ M，不作任何修改</li>
 * </ol>
 */
@Slf4j
@Component
public class L2HistoricalToolResultCleanup implements ContextCompactor {

    private final TokenCounter tokenCounter;
    private final ToolResultCacheService cacheService;
    @Value("${jchatmind.context-compactor.l2.cache-ttl-seconds:604800}")
    private int l2CacheTtlSeconds;

    public L2HistoricalToolResultCleanup(TokenCounter tokenCounter,
                                         ToolResultCacheService cacheService
    ) {
        this.tokenCounter = tokenCounter;
        this.cacheService = cacheService;
    }

    @Override
    public CompactResult compact(List<Message> messages, CompactorContext context) {
        int maxKept = context.getMaxKeptResults();
        if (maxKept <= 0 || messages.isEmpty()) {
            return CompactResult.continue_(messages, "L2");
        }

        // 步1: 找到所有 ToolResponseMessage 的位置（从末尾开始计数）
        // 因为我们需要"保留最近 M 条"，所以从后向前遍历更高效
        int toolRespCount = 0;
        List<Integer> toolRespIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolResponseMessage) {
                toolRespCount++;
                toolRespIndices.add(i);
            }
        }

        if (toolRespCount <= maxKept) {
            // 不超阈值，无需处理
            log.debug("[L2] 工具结果数量({})未超过阈值({})，跳过", toolRespCount, maxKept);
            return CompactResult.continue_(messages, "L2");
        }

        // 步2: 确定需要归档的索引范围（从0到倒数第maxKept个之前）
        int keepFromIndex = toolRespIndices.get(toolRespIndices.size() - maxKept);

        int beforeTokens = tokenCounter.estimateTokens(messages);
        int archivedCount = 0;

        // 步3: 构建新消息列表，替换旧工具结果
        List<Message> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof ToolResponseMessage toolResp
                    && i < keepFromIndex // keepFromIndex及以后得不再丢弃
                    && toolRespIndices.contains(i)) {

                // 归档每个 ToolResponse
                List<ToolResponseMessage.ToolResponse> replaced = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse resp : toolResp.getResponses()) {
                    String fullText = resp.responseData();
                    if (fullText == null || fullText.isEmpty()) {
                        replaced.add(resp);
                        continue;
                    }

                    // 存入 Redis 缓存
                    String cacheId = cacheService.store(
                            context.getSessionId(), resp.name(), fullText, l2CacheTtlSeconds);

                    String newContent;
                    if (cacheId != null) {
                        newContent = String.format(
                                ConstantPrompt.TOOL_RESULT_COMPACTED_SUFFIX,
                                cacheId, resp.name());
                    } else {
                        // 存储失败 → 原地硬截断（保留前200字符作为预览）
                        String preview = fullText.length() > 200
                                ? fullText.substring(0, 200) + "..."
                                : fullText;
                        newContent = "「此历史工具结果已被压缩（存储失败）。以下为预览：\n"
                                + preview + "」";
                    }

                    replaced.add(new ToolResponseMessage.ToolResponse(
                            resp.id(), resp.name(), newContent));
                    archivedCount++;
                }
                result.add(ToolResponseMessage.builder().responses(replaced).metadata(toolResp.getMetadata()).build());
            } else {
                result.add(msg);
            }
        }

        int afterTokens = tokenCounter.estimateTokens(result);
        boolean satisfied = afterTokens <= context.getTokenBudget();

        log.info("[L2] 历史工具结果清理: 共{}条, 保留最近{}条, 归档{}条, "
                        + "tokens {}→{}, satisfied={}",
                toolRespCount, maxKept, archivedCount,
                beforeTokens, afterTokens, satisfied);

        if (satisfied) {
            return CompactResult.pass(result, "L2");
        }
        return CompactResult.continue_(result, "L2");
    }
}
