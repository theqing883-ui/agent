package com.kaer.context.truncator;

import com.kaer.context.cache.ToolResponseCacheInterceptor;
import com.kaer.context.token.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.kaer.agent.ConstantPrompt.TOOL_WHITE_LIST;

/**
 * 上下文截断策略：工具响应截断 + 按 token 预算的消息截断。
 *
 * <p>v2.0 升级：集成 {@link ToolResponseCacheInterceptor}，
 * 将原来的"硬截断 + 固定后缀"升级为"Redis 缓存 + CacheId 后缀 + 按需阅读"方案。
 *
 * <p>白名单机制：标记为 {@code read_cache} 的工具响应享有更高的截断阈值（4x），
 * 确保 LLM 能够正常读取完整缓存内容而不会被二次截断。
 */
@Slf4j
@Component
public class ContextTruncator {

    /**
     * 白名单工具截断倍数：白名单工具的截断阈值 = 普通阈值 × 此倍数
     */
    private static final int WHITELIST_TOKENS_MULTIPLIER = 4;
    /**
     * 白名单工具绝对安全上限（token），防止极端情况
     */
    private static final int WHITELIST_ABSOLUTE_MAX_TOKENS = 16000;

    private final TokenCounter tokenCounter;
    private final ToolResponseCacheInterceptor cacheInterceptor;

    public ContextTruncator(TokenCounter tokenCounter,
                            ToolResponseCacheInterceptor cacheInterceptor) {
        this.tokenCounter = tokenCounter;
        this.cacheInterceptor = cacheInterceptor;
    }

    /**
     * 截断超出 token 上限的工具响应内容（v2.0：集成全局缓存+按需阅读）。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>遍历所有消息，仅处理 ToolResponseMessage 类型</li>
     *   <li>白名单工具（如 read_cache）享有更高截断阈值，允许更长内容进入上下文</li>
     *   <li>普通工具响应超阈值时，先尝试存入 Redis 缓存，再截断并附带 CacheId 后缀</li>
     *   <li>Redis 不可用时自动降级为硬截断 + 降级后缀</li>
     *   <li>未超阈值或非工具响应 → 原样保留</li>
     * </ol>
     *
     * @param messages              消息列表
     * @param maxToolResponseTokens 普通工具响应的最大 token 数限制
     * @param sessionId             会话 ID（用于缓存 Key 构造），为 null 时退化为纯硬截断
     * @return 处理后的消息列表
     */
    public List<Message> truncateToolResponses(List<Message> messages,
                                               int maxToolResponseTokens,
                                               String sessionId) {
        List<Message> result = new ArrayList<>();

        for (Message msg : messages) {
            if (msg instanceof ToolResponseMessage toolResp) {
                List<ToolResponseMessage.ToolResponse> capped = new ArrayList<>();

                for (ToolResponseMessage.ToolResponse resp : toolResp.getResponses()) {

                    // ===== 白名单放行 =====
                    // read_cache 等系统内部工具享有更高的截断阈值
                    if (isWhitelistedTool(resp.name())) {
                        int whitelistMax = Math.min(
                                maxToolResponseTokens * WHITELIST_TOKENS_MULTIPLIER,
                                WHITELIST_ABSOLUTE_MAX_TOKENS);
                        int tokens = tokenCounter.estimateTokens(resp.responseData());

                        if (tokens > whitelistMax) {
                            // 白名单工具也做安全截断，但阈值更高
                            String safeTruncated = truncateText(resp.responseData(), whitelistMax)
                                    + "\n\n...[白名单工具响应过长，已安全截断]";
                            capped.add(new ToolResponseMessage.ToolResponse(
                                    resp.id(), resp.name(), safeTruncated));
                            log.warn("[ContextTruncator] 白名单工具响应超过安全上限(={}t): tool={}, tokens={}",
                                    whitelistMax, resp.name(), tokens);
                        } else {
                            capped.add(resp);
                        }
                        continue;
                    }

                    // ===== 普通工具：缓存拦截代替硬截断 =====
                    if (sessionId != null) {
                        // 有会话上下文 → 走完整缓存流程
                        int shownTokens = Math.min(maxToolResponseTokens / 2, 2000);
                        ToolResponseMessage.ToolResponse processed =
                                cacheInterceptor.intercept(sessionId, resp, maxToolResponseTokens, shownTokens);
                        capped.add(processed);
                    } else {
                        // 无会话上下文 → 退化为旧硬截断逻辑
                        int tokens = tokenCounter.estimateTokens(resp.responseData());
                        if (tokens > maxToolResponseTokens) {
                            String truncated = truncateText(resp.responseData(), maxToolResponseTokens)
                                    + "\n\n...[内容过长，已截断]";
                            capped.add(new ToolResponseMessage.ToolResponse(
                                    resp.id(), resp.name(), truncated));
                        } else {
                            capped.add(resp);
                        }
                    }
                }
                result.add(ToolResponseMessage.builder().responses(capped).build());
            } else {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 截断超出 token 上限的工具响应内容（无 sessionId，退化为纯硬截断）。
     *
     * @deprecated 优先使用 {@link #truncateToolResponses(List, int, String)} 以启用缓存
     */
    @Deprecated
    public List<Message> truncateToolResponses(List<Message> messages, int maxToolResponseTokens) {
        return truncateToolResponses(messages, maxToolResponseTokens, null);
    }

    /**
     * 判断工具是否在白名单中——白名单工具的响应允许更长内容进入上下文。
     */
    private boolean isWhitelistedTool(String toolName) {
        return TOOL_WHITE_LIST.equals(toolName);
    }

    /**
     * 按 token 数截断文本，采用比例估算方法截取前缀。
     *
     * <p>算法原理：
     * 1. 计算文本的 token/字符比例（平均每个字符对应多少 token）
     * 2. 根据目标 token 数和比例反推需要保留的字符数
     * 3. 返回截取后的文本前缀
     *
     * <p>设计考虑：
     * - 使用比例估算避免重复调用 tokenCounter，提高性能
     * - 设置最小比例 0.25 避免除零错误和极端情况
     * - 确保至少保留 1 个字符
     *
     * @param text      待截断的文本
     * @param maxTokens 最大允许的 token 数
     * @return 截断后的文本前缀，不超过 maxTokens
     */
    private String truncateText(String text, int maxTokens) {
        // 空文本直接返回
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 计算 token/字符比例：平均每个字符对应多少 token
        double ratio = (double) tokenCounter.estimateTokens(text) / text.length();

        // 防止除零或极端比例，设置最小比例为 0.25
        if (ratio <= 0) ratio = 0.25;

        // 根据比例反推需要保留的字符数，至少保留 1 个字符
        int targetLen = Math.max(1, (int) (maxTokens / ratio));

        // 若目标长度大于等于原文本长度，直接返回原文本
        if (targetLen >= text.length()) {
            return text;
        }

        // 截取前缀并返回
        return text.substring(0, targetLen);
    }
}