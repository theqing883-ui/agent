package com.kaer.exception;

import lombok.Getter;

/**
 * 恢复操作超过最大尝试次数时抛出的异常。
 *
 * <p>触发场景：
 * <ul>
 *   <li>Max Tokens 续写重试超过 {@code ResilienceConfig.continuation.max-retries}（默认 3 次）</li>
 *   <li>上下文超限摘要降级失败后再次超限</li>
 * </ul>
 *
 * <p>此异常为终端异常，表示当前的 LLM 请求在当前配置下无法完成，需要人工介入或调整参数。
 */
@Getter
public class MaxRecoveryExceededException extends RuntimeException {

    /** 恢复策略名称（如 "continuation", "context-length"） */
    private final String strategy;

    /** 已尝试的次数 */
    private final int attempts;

    public MaxRecoveryExceededException(String strategy, int attempts) {
        super(String.format(
                "[Resilience] 恢复策略 '%s' 已达最大尝试次数上限（attempts=%d），请求无法自动恢复",
                strategy, attempts));
        this.strategy = strategy;
        this.attempts = attempts;
    }

    public MaxRecoveryExceededException(String strategy, int attempts, Throwable cause) {
        super(String.format(
                "[Resilience] 恢复策略 '%s' 已达最大尝试次数上限（attempts=%d），请求无法自动恢复",
                strategy, attempts), cause);
        this.strategy = strategy;
        this.attempts = attempts;
    }
}
