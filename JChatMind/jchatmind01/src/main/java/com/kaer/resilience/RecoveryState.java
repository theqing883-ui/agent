package com.kaer.resilience;

import lombok.Data;

/**
 * 错误恢复状态，绑定在 {@link com.kaer.agent.AgentContextHolder} 的 ThreadLocal 中，
 * 天然线程隔离，每个 Agent 线程拥有独立的恢复上下文。
 *
 * <p>生命周期：
 * <ul>
 *   <li>在 {@link ErrorRecoveryEngine#executeWithRecovery} 入口处初始化</li>
 *   <li>在 finally 块中由 {@link com.kaer.agent.AgentContextHolder#clearRecoveryState()} 清除</li>
 * </ul>
 *
 * <p>设计约束：
 * <ul>
 *   <li>Tomcat 线程池复用线程，必须在 finally 中强制清理，防止状态污染下一个请求</li>
 *   <li>所有字段均为非 final，支持在恢复流程中动态更新</li>
 * </ul>
 */
@Data
public class RecoveryState {

    // ==================== 529 过载恢复 ====================

    /** 连续 529 Overloaded 错误的次数，达到阈值后触发备胎模型切换 */
    private int consecutive529Count = 0;

    /** 最后收到 529 错误的时间戳（System.currentTimeMillis()），用于冷却窗口判断 */
    private long last529Timestamp = 0;

    // ==================== 备胎模型切换 ====================

    /** 当前线程最初使用的模型 ID（如 "deepseek-chat"），用于恢复后溯源 */
    private String originalModel;

    /** 当前实际生效的模型 ID（Fallback 后可能不同于 originalModel） */
    private String currentModel;

    /** 备胎模型是否已激活 */
    private boolean fallbackActive = false;

    // ==================== 临时故障（429 限流）计数 ====================

    /** 当前恢复周期内遇到的 429 / 临时网络错误累计次数 */
    private int transientRetryCount = 0;

    // ==================== Max Tokens 截断 / 续写 ====================

    /**
     * 续写重试次数（上限由 {@link ResilienceConfig.Continuation#getMaxRetries()} 控制，默认 3）。
     * <ul>
     *   <li>0 → 第一道防线：无痕升级 max_tokens 到 64K，不将残缺文本加入历史</li>
     *   <li>1-3 → 第二道防线：保留残骸，追加 "Resume directly" 提示后重试</li>
     *   <li>&gt;3 → 抛出 {@link MaxRecoveryExceededException}</li>
     * </ul>
     */
    private int continuationRetryCount = 0;

    // ==================== 上下文超限 ====================

    /** 上下文超限是否已经尝试过摘要降级（仅允许 1 次） */
    private boolean contextLengthRetried = false;

    // ==================== 无痕文本累加器（Response Stitching） ====================

    /**
     * 截断文本累加器 —— 在底层悄悄缓存每次截断的残缺文本，不污染全局 chatMemory。
     *
     * <p>设计意图：
     * <ul>
     *   <li>替代直接把残缺片段 {@code chatMemory.add()} 的污染行为</li>
     *   <li>续写 Prompt 重建时从累加器取出完整残骸追加到请求中</li>
     *   <li>最终拿到完整响应后，与本次文本拼接，向上层返回"从未截断过"的无痕结果</li>
     * </ul>
     */
    private StringBuilder accumulatedText = new StringBuilder();

    // ==================== 便捷方法 ====================

    /**
     * 向累加器追加截断文本片段。
     *
     * @param text 从截断 ChatResponse 中提取的残缺文本
     */
    public void appendText(String text) {
        if (text != null && !text.isEmpty()) {
            this.accumulatedText.append(text);
        }
    }

    /**
     * 获取累加器中的所有截断文本（用于续写 Prompt 和最终拼接）。
     *
     * @return 累积的完整残骸文本，可能为空字符串
     */
    public String getAccumulatedText() {
        return this.accumulatedText.toString();
    }

    /**
     * 是否已经缓存了截断文本（发生过至少一次截断）。
     *
     * @return true 表示执行过续写，需要在最终返回时做无痕拼接
     */
    public boolean hasAccumulatedText() {
        return !this.accumulatedText.isEmpty();
    }

    /**
     * 重置所有恢复状态（在 Agent 正常结束或清理时调用）。
     */
    public void reset() {
        consecutive529Count = 0;
        last529Timestamp = 0;
        originalModel = null;
        currentModel = null;
        fallbackActive = false;
        transientRetryCount = 0;
        continuationRetryCount = 0;
        contextLengthRetried = false;
        accumulatedText.setLength(0);
    }

    /**
     * 判断 529 连续计数是否已达到备胎切换阈值（默认 3）。
     */
    public boolean shouldFallback(int threshold) {
        return consecutive529Count >= threshold;
    }

    /**
     * 判断续写是否已超过最大允许次数。
     */
    public boolean isContinuationExceeded(int maxRetries) {
        return continuationRetryCount > maxRetries;
    }

    /**
     * 判断上下文超限是否已重试过。
     */
    public boolean isContextLengthAlreadyRetried() {
        return contextLengthRetried;
    }
}
