package com.kaer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 韧性重试配置 —— 协调 Spring AI 内置重试与 {@link com.kaer.resilience.ErrorRecoveryEngine}。
 *
 * <p><b>两层防线架构（防重试雪崩）：</b>
 * <pre>
 *   请求 → [第一层: Spring AI 内置重试 (max-attempts=2, 仅1次快速重试)]
 *              ↓ 失败
 *         [第二层: ErrorRecoveryEngine (语义级自愈)]
 *              ├─ 429 → 指数退避 + Jitter
 *              ├─ 529 → 指数退避 + 备胎模型切换
 *              ├─ Max Tokens 截断 → 无痕升级 + 续写
 *              └─ Context Length 超限 → 摘要降级
 * </pre>
 *
 * <p><b>设计理由：</b>
 * Spring AI 1.1.0 的 RetryUtils 不区分错误类型 —— 对 400「必败错误」也会重试。
 * 本配置将内置重试次数压缩为 1 次（max-attempts=2），将绝大多数恢复逻辑
 * 交给 {@code ErrorRecoveryEngine} 处理，避免在框架层死等空转。
 *
 * <p><b>注意：</b>Spring AI 1.1.0 使用内部 {@code RetryUtils} 而非暴露的
 * {@code RetryTemplate} bean，因此无法通过覆盖 bean 来精细控制重试策略。
 * 本配置通过 {@code spring.ai.retry.max-attempts=2} 限制框架层重试，
 * 确保错误快速穿透到 ErrorRecoveryEngine。
 *
 * @see com.kaer.resilience.ErrorRecoveryEngine
 */
@Slf4j
@Configuration
public class ResilienceRetryConfig {

    @Value("${spring.ai.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${spring.ai.retry.backoff.initial-interval:2000}")
    private long initialIntervalMs;

    @Value("${spring.ai.retry.backoff.multiplier:2}")
    private double multiplier;

    @Value("${spring.ai.retry.backoff.max-interval:30000}")
    private long maxIntervalMs;

    @PostConstruct
    public void logRetryConfig() {
        log.info("[Resilience] Spring AI 内置重试: maxAttempts={}, initialInterval={}ms, "
                        + "multiplier={}, maxInterval={}ms. "
                        + "业务级自愈由 ErrorRecoveryEngine 接管（429/529/400 不进框架重试循环）",
                maxAttempts, initialIntervalMs, multiplier, maxIntervalMs);
    }
}
