package com.kaer.agent;

import com.kaer.resilience.RecoveryState;

/**
 * Agent 上下文持有者——使用 ThreadLocal 存储当前线程运行的 Agent 的会话信息。
 * <p>
 * 用于在多 Agent 任务委派场景中，让 {@link com.kaer.agent.tools.DelegationTool}
 * 能够获取当前父 Agent 的 sessionId 和 agentId，以便创建子会话时建立父子关联。
 * <p>
 * 每个 Agent 实例在独立的线程中运行（由 {@code @Async} 保证），
 * 因此 ThreadLocal 能够正确隔离不同 Agent 的上下文。
 *
 * <p><b>ThreadLocal 防污染（至关重要）：</b>
 * Spring Boot 依赖 Tomcat 线程池，线程会被复用。如果任务直接崩溃，
 * 遗留在 ThreadLocal 中的 {@link RecoveryState} 会污染下一个分配到该线程的用户请求。
 * 因此调用方必须在 {@code finally} 块中显式调用 {@link #clear()}。
 */
public class AgentContextHolder {

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_AGENT_ID = new ThreadLocal<>();

    /**
     * 韧性恢复状态 —— 绑定在当前线程，与 Agent 生命周期一致。
     * 由 {@link com.kaer.resilience.ErrorRecoveryEngine} 在调用入口初始化，
     * 在 finally 块中清除。
     */
    private static final ThreadLocal<RecoveryState> RECOVERY_STATE = new ThreadLocal<>();

    private AgentContextHolder() {
        // 工具类，禁止实例化
    }

    /**
     * 设置当前线程的 Agent 上下文。
     *
     * @param sessionId 当前会话 ID
     * @param agentId   当前 Agent ID
     */
    public static void set(String sessionId, String agentId) {
        CURRENT_SESSION_ID.set(sessionId);
        CURRENT_AGENT_ID.set(agentId);
    }

    /**
     * 获取当前线程的会话 ID。
     *
     * @return 当前会话 ID，如果未设置则返回 null
     */
    public static String getSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    /**
     * 获取当前线程的 Agent ID。
     *
     * @return 当前 Agent ID，如果未设置则返回 null
     */
    public static String getAgentId() {
        return CURRENT_AGENT_ID.get();
    }

    // ==================== RecoveryState ThreadLocal 管理 ====================

    /**
     * 获取当前线程的 {@link RecoveryState}，如果不存在则创建新实例。
     *
     * @return 当前线程的 RecoveryState（非 null）
     */
    public static RecoveryState getRecoveryState() {
        RecoveryState state = RECOVERY_STATE.get();
        if (state == null) {
            state = new RecoveryState();
            RECOVERY_STATE.set(state);
        }
        return state;
    }

    /**
     * 直接设置当前线程的 {@link RecoveryState}（用于 Engine 入口初始化）。
     *
     * @param state 恢复状态实例
     */
    public static void setRecoveryState(RecoveryState state) {
        RECOVERY_STATE.set(state);
    }

    /**
     * 清除当前线程的 RecoveryState，防止线程池复用时的状态污染。
     * <p>
     * 调用时机：ErrorRecoveryEngine.executeWithRecovery() 的 finally 块，
     * 以及 JChatMind.run() 的 finally 块（兜底清理）。
     */
    public static void clearRecoveryState() {
        RecoveryState state = RECOVERY_STATE.get();
        if (state != null) {
            state.reset();
        }
        RECOVERY_STATE.remove();
    }

    /**
     * 清除当前线程的全部上下文（在 Agent 结束后调用，防止内存泄漏）。
     * <p>
     * 同时清理 Session/Agent ID 和 RecoveryState。
     */
    public static void clear() {
        CURRENT_SESSION_ID.remove();
        CURRENT_AGENT_ID.remove();
        clearRecoveryState();
    }
}
