package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import com.kaer.context.cache.ToolResultCacheService;
import com.kaer.context.cache.ToolResultCacheService.CacheReadResult;
import com.kaer.context.token.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 系统级内部工具：供 LLM 按需读取被缓存的工具响应（统一 Redis 后端）。
 *
 * <p><b>安全防御机制（防止二次 OOM）:</b>
 * <ol>
 *   <li><b>强制分块上限</b>：单次读取最多返回 {@link #MAX_READ_CHARS} 字符（约 4000 tokens），
 *       即使 LLM 传入更大的 length 参数也会被强制钳制</li>
 *   <li><b>位置标记</b>：每次返回都附带精确的位置信息（已读/总共），
 *       让 LLM 知道是否还有更多内容需要分页读取</li>
 *   <li><b>白名单放行</b>：该工具的返回结果在 {@code ContextTruncator} 中被识别为可信响应，
 *       允许比普通工具响应更长的内容进入上下文（4x 阈值）</li>
 *   <li><b>越界保护</b>：offset 超限时返回友好提示而非报错</li>
 *   <li><b>缓存穿透防护</b>：非法 CacheId 格式直接拒绝，不查询 Redis</li>
 *   <li><b>TTL 续期</b>：每次读取成功后刷新 Redis TTL，防止 LLM 分页中途过期</li>
 * </ol>
 *
 * <p><b>注册策略:</b>
 * 该工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认加载，不可被禁用。
 * 在子 Agent 中同样注册（子 Agent 也可能遇到超长工具响应）。
 *
 * <p><b>CacheId 格式:</b> {@code toolName:xxxxxxxx}（工具名 + 冒号 + 8 位十六进制 UUID 前缀）
 * <br>示例: {@code databaseQuery:a1b2c3d4}
 */
@Slf4j
@Component
public class ReadCacheTool implements Tool {

    /** 单次读取最大字符数 —— 约 16000 tokens，硬上限不可被 LLM 突破 */
    private static final int MAX_READ_CHARS = 16000;
    /** 默认读取字符数 */
    private static final int DEFAULT_READ_CHARS = 8000;
    /** CacheId 格式校验：toolName:xxxxxxxx (8 位十六进制) */
    private static final Pattern CACHE_ID_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*:[a-f0-9]{8}$");

    private final ToolResultCacheService cacheService;
    private final TokenCounter tokenCounter;

    public ReadCacheTool(ToolResultCacheService cacheService,
                         TokenCounter tokenCounter) {
        this.cacheService = cacheService;
        this.tokenCounter = tokenCounter;
    }

    // ===== Tool 接口实现 =====

    @Override
    public String getName() {
        return "readCacheTool";
    }

    @Override
    public String getDescription() {
        return "读取之前被截断的工具响应的完整缓存内容。当工具返回结果被截断且你需要更多信息时使用。支持分页读取以避免过长的响应。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    // ===== 核心方法 —— 被 LLM 通过 Tool Calling 调用 =====

    /**
     * 按分页参数读取缓存的工具响应内容。
     *
     * @param cacheId 缓存编号（必填），格式为 "toolName:xxxxxxxx"
     * @param offset  起始字符位置（可选，默认 0）
     * @param length  读取的最大字符数（可选，默认 8000，最大 16000）
     * @return 分页后的缓存内容，附带位置标记和分页引导
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "readCache",
            description = """
                    读取之前被截断的工具响应的完整缓存内容。
                    当工具返回结果被截断且你需要更多信息来准确回答用户问题时使用。
                    支持分页读取以避免过长的响应。
                    """
    )
    public String readCache(
            @org.springframework.ai.tool.annotation.ToolParam(description = "截断提示中提供的缓存编号，格式为 toolName:xxxxxxxx")
            String cacheId,
            @org.springframework.ai.tool.annotation.ToolParam(description = "从缓存内容的第几个字符开始读取，默认为 0", required = false)
            Integer offset,
            @org.springframework.ai.tool.annotation.ToolParam(description = "读取的最大字符数，默认 8000，最大 16000", required = false)
            Integer length) {

        // ===== 1. 参数校验 =====
        if (cacheId == null || cacheId.isBlank()) {
            return buildError(" 参数错误: cacheId 不能为空。请从工具响应的截断提示中获取缓存编号。");
        }

        // CacheId 格式校验（防止 LLM 幻觉编造）
        if (!CACHE_ID_PATTERN.matcher(cacheId).matches()) {
            return buildError(String.format(
                    " 无效的缓存编号格式: %s\n"
                            + "缓存编号格式应为: toolName:xxxxxxxx (工具名 + 冒号 + 8 位十六进制字符)\n"
                            + "请检查截断提示中的缓存编号是否完整复制。",
                    cacheId));
        }

        // 安全钳制: offset 不能为负
        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        // 安全钳制: length 必须在 [1, MAX_READ_CHARS] 范围内
        // 这是防止二次 OOM 的核心防线：无论 LLM 传入多大的 length，都被钳制
        int safeLength;
        if (length == null) {
            safeLength = DEFAULT_READ_CHARS;
        } else if (length <= 0) {
            safeLength = DEFAULT_READ_CHARS;
        } else {
            safeLength = Math.min(length, MAX_READ_CHARS);
        }

        // 超过上限时记录（监控 LLM 是否有大量拉取倾向）
        if (length != null && length > MAX_READ_CHARS) {
            log.warn("[ReadCacheTool] LLM 请求读取 {} 字符，已钳制为 {}: cacheId={}",
                    length, MAX_READ_CHARS, cacheId);
        }

        // ===== 2. 获取当前会话上下文 =====
        String sessionId = AgentContextHolder.getSessionId();
        if (sessionId == null) {
            return buildError(" 系统错误: 无法获取当前会话上下文。请稍后重试。");
        }

        // ===== 3. 从 Redis 读取缓存 =====
        CacheReadResult result = cacheService.read(sessionId, cacheId, safeOffset, safeLength);

        if (result == null) {
            return buildError(String.format(
                    """
                     缓存未找到或已过期: cacheId=%s

                    可能原因:
                    1. 缓存已超过有效期自动清除
                    2. CacheId 输入有误，请检查截断提示中的缓存编号是否完整
                    3. 该会话已结束，缓存已被清理

                    建议: 如果确实需要完整数据，请重新触发原始工具调用。
                    """,
                    cacheId));
        }

        // ===== 4. 构建返回内容 =====
        StringBuilder response = new StringBuilder();

        // 位置信息头部
        response.append(result.positionInfo());
        response.append("\n");
        response.append("─".repeat(60));
        response.append("\n\n");

        // 内容片段
        response.append(result.content());

        // 如果还有更多内容，追加分页引导
        if (result.offsetEnd() < result.totalChars()) {
            int remaining = result.totalChars() - result.offsetEnd();
            response.append(String.format("""
                    ─────────────────────────────────────────────
                     还有 %d 字符未读取 (%.1f%% 剩余)
                     继续读取下一页:
                      read_cache(cacheId="%s", offset=%d, length=%d)
                    """,
                    remaining,
                    (remaining * 100.0 / result.totalChars()),
                    cacheId,
                    result.offsetEnd(),
                    safeLength
            ));
        } else {
            response.append("""


                    ─────────────────────────────────────────────
                    已到达缓存末尾
                    """);
        }

        // 记录审计日志（含 token 估算）
        int responseTokens = tokenCounter.estimateTokens(response.toString());
        log.info("[ReadCacheTool] 返回缓存内容: cacheId={}, offset={}-{}, "
                        + "chars={}, ~tokens={}, hasMore={}",
                cacheId, result.offsetStart(), result.offsetEnd(),
                result.content().length(), responseTokens,
                result.offsetEnd() < result.totalChars());

        return response.toString();
    }

    // ===== 私有方法 =====

    private String buildError(String message) {
        log.warn("[ReadCacheTool] {}", message.replace("\n", " | "));
        return message;
    }
}