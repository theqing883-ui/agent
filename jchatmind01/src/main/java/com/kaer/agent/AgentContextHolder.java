package com.kaer.agent;

/**
 * Agent 上下文持有者——使用 ThreadLocal 存储当前线程运行的 Agent 的会话信息。
 * <p>
 * 用于在多 Agent 任务委派场景中，让 {@link com.kaer.agent.tools.DelegationTool}
 * 能够获取当前父 Agent 的 sessionId 和 agentId，以便创建子会话时建立父子关联。
 * <p>
 * 每个 Agent 实例在独立的线程中运行（由 {@code @Async} 保证），
 * 因此 ThreadLocal 能够正确隔离不同 Agent 的上下文。
 */
public class AgentContextHolder {

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_AGENT_ID = new ThreadLocal<>();

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

    /**
     * 清除当前线程的上下文（在 Agent 结束后调用，防止内存泄漏）。
     */
    public static void clear() {
        CURRENT_SESSION_ID.remove();
        CURRENT_AGENT_ID.remove();
    }
}
