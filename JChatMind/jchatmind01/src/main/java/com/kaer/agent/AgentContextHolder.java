package com.kaer.agent;

import com.kaer.resilience.RecoveryState;

import java.util.function.Consumer;

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

    /**
     * Lead Agent 名称 —— Teammate 需要知道回复谁。
     * 由 {@link TeammateWorker} 在调用 agent.run() 前设置。
     */
    private static final ThreadLocal<String> LEAD_NAME = new ThreadLocal<>();

    /**
     * PendingRequest 注册回调 —— SendMessageTool 发送 REQUEST 后，
     * 通过此回调将 PendingRequest 注册到 JChatMind.pendingRequests 中。
     * 由 JChatMind.run() 在设置上下文时注册。
     */
    private static final ThreadLocal<Consumer<PendingRequest>> PENDING_REQUEST_CALLBACK = new ThreadLocal<>();

    /**
     * 队友优雅停止标志 —— ShutdownTool 调用后置为 true，
     * TeammateWorker 在 agent.run() 返回后检查此标志，决定是否重启。
     */
    private static final ThreadLocal<Boolean> NEEDS_SHUTDOWN = new ThreadLocal<>();

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

    // ==================== Lead Name ThreadLocal 管理 ====================

    public static String getLeadName() {
        return LEAD_NAME.get();
    }

    public static void setLeadName(String name) {
        LEAD_NAME.set(name);
    }

    // ==================== PendingRequest Callback ThreadLocal 管理 ====================

    public static Consumer<PendingRequest> getPendingRequestCallback() {
        return PENDING_REQUEST_CALLBACK.get();
    }

    public static void setPendingRequestCallback(Consumer<PendingRequest> cb) {
        PENDING_REQUEST_CALLBACK.set(cb);
    }

    // ==================== Shutdown 标志管理 ====================

    public static void markNeedsShutdown() { NEEDS_SHUTDOWN.set(true); }
    public static boolean needsShutdown() { return Boolean.TRUE.equals(NEEDS_SHUTDOWN.get()); }

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
        LEAD_NAME.remove();
        PENDING_REQUEST_CALLBACK.remove();
        NEEDS_SHUTDOWN.remove();
        clearRecoveryState();
    }

    public static void clearSessionIdAndAgentIdAndPendingRequest() {
        CURRENT_SESSION_ID.remove();
        CURRENT_AGENT_ID.remove();
        PENDING_REQUEST_CALLBACK.remove();
        NEEDS_SHUTDOWN.remove();
    }

    public static void clearLeadName() {
        LEAD_NAME.remove();
        NEEDS_SHUTDOWN.remove();
    }
}
