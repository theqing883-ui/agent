package com.kaer.context.cache;

import com.kaer.context.token.TokenCounter;
import com.kaer.context.truncator.CacheTruncationSuffixBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具响应缓存拦截器——"全局缓存 + 按需阅读"方案的核心编排组件。
 *
 * <p>在工具响应进入 LLM 上下文之前执行拦截，判断是否需要缓存+截断。
 * 这是整个方案的总流量入口，所有工具响应都经过此组件。
 *
 * <p>决策逻辑（每条工具响应独立判断）：
 * <pre>
 *   if tokenCount {@code <=} triggerTokens:
 *       → 直接返回原文（不截断，不缓存，零开销）
 *   else:
 *       → 尝试存入 Redis → 生成 CacheId
 *         ├─ 成功: 截断 + 拼接 CacheId 后缀
 *         └─ 失败: 硬截断 + 降级后缀（无 CacheId）
 * </pre>
 *
 * <p>降级保证：Redis 不可用时自动回退到硬截断，Agent 正常运行不中断。
 */
@Slf4j
@Component
public class ToolResponseCacheInterceptor {

    /** 默认触发缓存的阈值：单条工具响应超过此 token 数时触发缓存 */
    private static final int DEFAULT_CACHE_TRIGGER_TOKENS = 4000;
    /** 截断后展示给 LLM 的默认 token 数 */
    private static final int DEFAULT_SHOWN_TOKENS = 2000;

    private final ToolResultCacheService cacheService;
    private final CacheTruncationSuffixBuilder suffixBuilder;
    private final TokenCounter tokenCounter;

    public ToolResponseCacheInterceptor(ToolResultCacheService cacheService,
                                         CacheTruncationSuffixBuilder suffixBuilder,
                                         TokenCounter tokenCounter) {
        this.cacheService = cacheService;
        this.suffixBuilder = suffixBuilder;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 对单条工具响应执行缓存拦截。
     *
     * @param sessionId     会话 ID（用于构造 Redis Key）
     * @param toolResponse  原始工具响应
     * @param triggerTokens 触发缓存的 token 阈值（0 表示使用默认值）
     * @param shownTokens   截断后展示的 token 数（0 表示使用默认值）
     * @return 处理后的 ToolResponse（原文，或截断+后缀版本）
     */
    public ToolResponseMessage.ToolResponse intercept(
            String sessionId,
            ToolResponseMessage.ToolResponse toolResponse,
            int triggerTokens,
            int shownTokens) {

        // 使用默认值填充
        int effectiveTrigger = triggerTokens > 0 ? triggerTokens : DEFAULT_CACHE_TRIGGER_TOKENS;
        int effectiveShown = shownTokens > 0 ? shownTokens : DEFAULT_SHOWN_TOKENS;

        String fullText = toolResponse.responseData();

        // 空响应直接返回
        if (fullText.isEmpty()) {
            return toolResponse;
        }

        int tokenCount = tokenCounter.estimateTokens(fullText);

        // 不超阈值 → 原样返回，零开销
        if (tokenCount <= effectiveTrigger) {
            log.debug("[CacheInterceptor] 工具响应未超阈值，直接通过: tool={}, tokens={}",
                    toolResponse.name(), tokenCount);
            return toolResponse;
        }

        // ===== 超阈值 → 缓存 + 截断流程 =====

        // 步1: 尝试存入 Redis
        String cacheId = cacheService.store(sessionId, toolResponse.name(), fullText);

        // 步2: 截断文本（保留前 shownTokens 对应的字符）
        String shownText = truncateByTokens(fullText, effectiveShown);

        // 步3: 构建后缀
        String suffix;
        if (cacheId != null) {
            // 缓存成功 → 附带 CacheId 的完整后缀
            suffix = suffixBuilder.build(toolResponse.name(), cacheId, fullText, shownText);
        } else {
            // 缓存失败（Redis 不可用 / 内容过大）→ 降级后缀
            suffix = suffixBuilder.buildFallback(fullText.length(), tokenCount, shownText.length());
        }

        // 步4: 拼接
        String finalText = shownText + suffix;

        log.info("[CacheInterceptor] 工具响应已处理: tool={}, cacheId={}, "
                        + "fullTokens={}, shownTokens={}, fullChars={}, cached={}",
                toolResponse.name(), cacheId, tokenCount, effectiveShown,
                fullText.length(), cacheId != null);

        return new ToolResponseMessage.ToolResponse(
                toolResponse.id(), toolResponse.name(), finalText);
    }



    /**
     * 按 token 数截断文本前缀（与 ContextTruncator.truncateText 算法一致）。
     */
    private String truncateByTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        double ratio = (double) tokenCounter.estimateTokens(text) / text.length();
        if (ratio <= 0) {
            ratio = 0.25;
        }

        int targetLen = Math.max(1, (int) (maxTokens / ratio));
        if (targetLen >= text.length()) {
            return text;
        }

        return text.substring(0, targetLen);
    }
}