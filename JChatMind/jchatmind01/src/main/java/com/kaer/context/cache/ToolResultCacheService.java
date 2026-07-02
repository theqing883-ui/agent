package com.kaer.context.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

import java.util.UUID;

import static com.kaer.agent.ConstantPrompt.KEY_PREFIX;

/**
 * 工具响应全局缓存服务，负责将超大工具响应存入 Redis 并提供分页读取。
 *
 * <p>内存泄漏防护（三重保障）：
 * <ol>
 *   <li>写入时 SETEX 设置 TTL，到期自动删除</li>
 *   <li>会话结束时 SCAN + DEL 批量清理该会话所有缓存</li>
 *   <li>Redis 服务端 maxmemory-policy allkeys-lru 兜底淘汰</li>
 * </ol>
 *
 * <p>降级策略：Redis 不可用时返回 null，由调用方回退到硬截断。
 *
 * <p>实现了 {@link ToolResultStorage} 接口，作为工具结果存储的 Redis 后端。
 */
@Slf4j
@Component
public class ToolResultCacheService implements ToolResultStorage {

    /**
     * 单条工具响应最大缓存大小（1 MB），超过则不缓存直接硬截断
     */
    private static final int MAX_CACHE_BYTES = 1_048_576;

    private final JedisPooled jedis;

    @Value("${jchatmind.tool-cache.ttl-seconds:600}")
    private int ttlSeconds;// 10 分钟

    public ToolResultCacheService(JedisPooled jedis) {
        this.jedis = jedis;
    }

    /**
     * 将工具响应存入 Redis 并返回 CacheId。
     *
     * @param sessionId  会话 ID
     * @param toolName   工具名称
     * @param fullResult 完整的工具响应文本
     * @return 生成的 CacheId；Redis 不可用或内容过大时返回 null
     */
    // 方法一：使用默认 TTL
    @Override
    public String store(String sessionId, String toolName, String fullResult) {
        // 直接委托给方法二，传入类级别的默认 this.ttlSeconds
        return store(sessionId, toolName, fullResult, this.ttlSeconds);
    }

    /**
     * 存储工具响应（指定 TTL），供 L2 历史工具结果清理等需要更长过期时间的场景使用。
     *
     * @param sessionId        会话 ID
     * @param toolName         工具名称
     * @param fullResult       完整的工具响应文本
     * @param customTtlSeconds 自定义过期时间（秒）
     * @return 生成的 CacheId；Redis 不可用或内容过大时返回 null
     */
    // 方法二：核心实现，使用自定义 TTL
    @Override
    public String store(String sessionId, String toolName, String fullResult, int customTtlSeconds) {
        // 大小保护：超过 1MB 不缓存
        if (fullResult != null && fullResult.length() > MAX_CACHE_BYTES) {
            log.warn("[ToolCache] 工具响应过大({} bytes)，超过 {} bytes 上限，跳过缓存: tool={}",
                    fullResult.length(), MAX_CACHE_BYTES, toolName);
            return null;
        }

        // 生成唯一 CacheId
        String cacheId = toolName + ":" + UUID.randomUUID().toString().substring(0, 8);

        try {
            String dataKey = buildDataKey(sessionId, cacheId);
            String metaKey = buildMetaKey(sessionId, cacheId);

            // 存入主数据（使用传入的 TTL）
            jedis.setex(dataKey, customTtlSeconds, fullResult);

            // 存入元数据（统一加上 ttlSeconds 字段记录）
            String metaJson = String.format(
                    "{\"toolName\":\"%s\",\"sessionId\":\"%s\",\"totalChars\":%d,\"createdAt\":\"%s\",\"ttlSeconds\":%d}",
                    escapeJson(toolName), escapeJson(sessionId),
                    fullResult.length(), java.time.Instant.now().toString(), customTtlSeconds
            );
            jedis.setex(metaKey, customTtlSeconds, metaJson);

            log.info("[ToolCache] 已缓存工具响应: cacheId={}, totalChars={}, ttl={}s",
                    cacheId, fullResult.length(), customTtlSeconds);
            return cacheId;

        } catch (Exception e) {
            log.error("[ToolCache] Redis 写入失败，降级为硬截断: tool={}, sessionId={}", toolName, sessionId, e);
            return null;
        }
    }

    /**
     * 按分页参数读取缓存内容。读取成功后自动续期 TTL。
     *
     * @param sessionId 会话 ID
     * @param cacheId   缓存编号
     * @param offset    起始字符位置（0-based）
     * @param length    读取字符数
     * @return 分页结果；缓存不存在时返回 null
     */
    @Override
    public CacheReadResult read(String sessionId, String cacheId, int offset, int length) {
        String dataKey = buildDataKey(sessionId, cacheId);

        try {
            String fullResult = jedis.get(dataKey);

            if (fullResult == null) {
                log.warn("[ToolCache] 缓存未命中: cacheId={}, sessionId={}", cacheId, sessionId);
                return null;
            }

            int totalChars = fullResult.length();

            // 边界保护
            if (offset >= totalChars) {
                return new CacheReadResult("", offset, totalChars, totalChars,
                        "[已到达缓存末尾，无更多内容]");
            }

            // 安全截取
            int endIndex = Math.min(offset + length, totalChars);
            String fragment = fullResult.substring(offset, endIndex);

            // 重置 TTL：防止 LLM 多次分页读取时缓存中途过期
            jedis.expire(dataKey, ttlSeconds);

            double pct = (endIndex * 100.0 / totalChars);
            String positionInfo = String.format(
                    "[缓存读取: 字符 %d-%d / 共 %d (%.1f%%)]",
                    offset, endIndex, totalChars, pct);

            log.info("[ToolCache] 缓存读取: cacheId={}, offset={}, length={}, actualRead={}",
                    cacheId, offset, length, fragment.length());

            return new CacheReadResult(fragment, offset, endIndex, totalChars, positionInfo);

        } catch (Exception e) {
            log.error("[ToolCache] Redis 读取失败: cacheId={}, sessionId={}", cacheId, sessionId, e);
            return null;
        }
    }

    /**
     * 删除指定 CacheId 的缓存。
     */
    @Override
    public void delete(String sessionId, String cacheId) {
        try {
            jedis.del(buildDataKey(sessionId, cacheId));
            jedis.del(buildMetaKey(sessionId, cacheId));
        } catch (Exception e) {
            log.warn("[ToolCache] 删除缓存失败: cacheId={}, sessionId={}", cacheId, sessionId, e);
        }
    }


    // ==================== 私有方法 ====================

    private String buildDataKey(String sessionId, String cacheId) {
        return KEY_PREFIX + ":" + sessionId + ":" + cacheId;
    }

    private String buildMetaKey(String sessionId, String cacheId) {
        return KEY_PREFIX + ":meta:" + sessionId + ":" + cacheId;
    }

    /**
     * 对 JSON 字符串值中的特殊字符做简单转义。
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== 内部类 ====================

    /**
     * 缓存读取结果，包含分页片段和位置信息。
     */
    public record CacheReadResult(
            String content,
            int offsetStart,
            int offsetEnd,
            int totalChars,
            String positionInfo
    ) {
    }
}