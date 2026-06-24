package com.kaer.resilience;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 韧性引擎配置，读取 {@code application.yml} 中 {@code resilience} 前缀的配置。
 *
 * <p>配置示例：
 * <pre>{@code
 * resilience:
 *   fallback:
 *     deepseek-chat: glm-4.6
 *     glm-4.6: deepseek-chat
 *   backoff:
 *     base-delay-ms: 500
 *     max-delay-ms: 30000
 *     jitter-ratio: 0.25
 *   continuation:
 *     max-retries: 3
 *     upgraded-tokens: 64000
 *   context-length:
 *     max-retries: 1
 * }</pre>
 *
 * <p>线程安全：本配置为启动时一次性注入的不可变值（运行时只读），天然线程安全。
 */
@Data
@Component
@ConfigurationProperties(prefix = "resilience")
public class ResilienceConfig {

    /**
     * 备胎模型映射表：key = 当前模型 ID，value = 降级后使用的备胎模型 ID。
     * 示例：{"deepseek-chat": "glm-4.6", "glm-4.6": "deepseek-chat"}
     */
    private Map<String, String> fallback = Map.of(
            "deepseek-chat", "glm-4.6",// key和value是不一样的模型
            "glm-4.6", "deepseek-chat"
    );

    /** 退避策略参数 */
    private Backoff backoff = new Backoff();

    /** Max Tokens 截断 / 续写策略参数 */
    private Continuation continuation = new Continuation();

    /** 上下文超限降级策略参数 */
    private ContextLength contextLength = new ContextLength();

    // ==================== 内部配置类 ====================

    @Data
    public static class Backoff {
        /** 基础延迟（毫秒），公式：baseDelayMs * (2 ^ attempt) */
        private long baseDelayMs = 500;
        /** 最大延迟上限（毫秒），防止无限增长 */
        private long maxDelayMs = 30_000;
        /** Jitter 比例，随机范围 [0, jitterRatio * delay] */
        private double jitterRatio = 0.25;
        /** 529 连续错误触发备胎切换的阈值 */
        private int fallback529Threshold = 3;
    }

    @Data
    public static class Continuation {
        /** 续写最大重试次数（不含首次无痕升级），默认 3 */
        private int maxRetries = 3;
        /** 无痕升级时使用的 max_tokens 值（64K） */
        private int upgradedTokens = 64_000;
    }

    @Data
    public static class ContextLength {
        /** 上下文超限摘要降级最多尝试次数，默认 1 */
        private int maxRetries = 1;
        /** 降级后保留的最近关键对话条数 */
        private int reservedRecentMessages = 3;
    }

    // ==================== 便捷查询方法 ====================

    /**
     * 根据当前模型名查找备胎模型名，未配置则返回 null。
     */
    public String getFallbackModel(String currentModel) {
        return fallback != null ? fallback.get(currentModel) : null;
    }
}
