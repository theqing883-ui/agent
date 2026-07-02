package com.kaer.context.cache;

import java.util.Optional;

/**
 * 工具结果持久化存储抽象接口。
 *
 * <p>定义超大工具响应内容的存取规范，统一使用 Redis 作为存储后端：
 * <ul>
 *   <li>Redis — {@code ToolResultCacheService}（主实现，供 L2 历史清理和缓存截断使用）</li>
 * </ul>
 *
 * <p>降级约定：{@link #store} 返回 {@code null} 时，调用方应回退到原地硬截断。
 *
 * @see com.kaer.context.cache.ToolResultCacheService
 */
public interface ToolResultStorage {

    /**
     * 存储完整工具结果并返回唯一缓存 ID。
     *
     * @param sessionId  会话 ID
     * @param toolName   工具名称
     * @param fullResult 完整结果文本
     * @return 缓存 ID（格式取决于实现）；存储失败时返回 {@code null}
     */
    String store(String sessionId, String toolName, String fullResult);

    /**
     * 存储完整工具结果（指定 TTL）并返回唯一缓存 ID。
     *
     * @param sessionId  会话 ID
     * @param toolName   工具名称
     * @param fullResult 完整结果文本
     * @param ttlSeconds 自定义过期时间（秒）；实现可能忽略此参数
     * @return 缓存 ID；存储失败时返回 {@code null}
     */
    default String store(String sessionId, String toolName, String fullResult, int ttlSeconds) {
        return store(sessionId, toolName, fullResult);
    }

    /**
     * 按缓存 ID 读取存储的完整内容。
     *
     * @param sessionId 会话 ID
     * @param cacheId   缓存 ID（由 {@link #store} 返回）
     * @return 完整内容；未找到或已过期时返回 {@link Optional#empty()}
     */
   ToolResultCacheService.CacheReadResult read(String sessionId, String cacheId, int offset, int length);

    /**
     * 删除单条缓存。
     *
     * @param sessionId 会话 ID
     * @param cacheId   缓存 ID
     */
    void delete(String sessionId, String cacheId);
}
