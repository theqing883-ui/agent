package com.kaer.context.truncator;

import com.kaer.context.token.TokenCounter;
import org.springframework.stereotype.Component;

import static com.kaer.agent.ConstantPrompt.TOOL_CACHE_TRUNCATION_SUFFIX;
import static com.kaer.agent.ConstantPrompt.TOOL_TRUNCATION_SUFFIX;

/**
 * 截断后缀构建器——为被缓存+截断的工具响应生成 LLM 可理解的后缀提示。
 *
 * <p>设计目标：让 LLM 100% 知道：
 * <ol>
 *   <li>内容已被截断并缓存</li>
 *   <li>缓存编号是多少</li>
 *   <li>如何调用 read_cache 工具读取剩余内容</li>
 *   <li>具体的调用示例</li>
 * </ol>
 *
 * <p>后缀使用中文（匹配系统 Prompt 语言），关键参数名 cacheId/offset/length 保留英文。
 */
@Component
public class CacheTruncationSuffixBuilder {

    private final TokenCounter tokenCounter;

    public CacheTruncationSuffixBuilder(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    /**
     * 构建截断后缀 Prompt。
     *
     * @param toolName  工具名称
     * @param cacheId   缓存编号
     * @param fullText  原始完整文本（用于计算实际 Token 数）
     * @param shownText 已展示给 LLM 的截断文本（用于计算隐藏量）
     * @return 格式化后的截断后缀
     */
    public String build(String toolName, String cacheId, String fullText, String shownText) {
        int totalChars = fullText.length();
        int totalTokens = tokenCounter.estimateTokens(fullText);
        int shownChars = shownText.length();
        int remainingChars = totalChars - shownChars;

        // 对剩余部分做 token 估算
        int remainingTokens = 0;
        if (remainingChars > 0 && shownChars < totalChars) {
            remainingTokens = tokenCounter.estimateTokens(
                    fullText.substring(shownChars, totalChars));
        }

        return String.format(TOOL_CACHE_TRUNCATION_SUFFIX,
                totalChars, totalTokens,
                toolName,
                remainingChars, remainingTokens,
                cacheId,
                cacheId,
                cacheId, cacheId, cacheId
        );
    }

    /**
     * 构建降级后缀（Redis 不可用时使用，不含 CacheId）。
     */
    public String buildFallback(int totalChars, int totalTokens, int shownChars) {
        int remainingChars = totalChars - shownChars;
        return String.format(TOOL_TRUNCATION_SUFFIX,
                totalChars, totalTokens,
                remainingChars,
                remainingChars > 0 ? tokenCounter.estimateTokens("…") : 0
        );
    }
}
