package com.kaer.agent;

import com.kaer.agent.messagebus.MessageBus;
import com.kaer.config.TeammateConfig;
import com.kaer.context.memory.TokenAwareChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 后台队友 Worker——将 JChatMind 的 think-execute 循环包装为守护线程。
 *
 * <h3>设计理念</h3>
 * <p>TeammateWorker 本身只是一个<strong>重启器</strong>，不包含业务协调逻辑。
 * 队友的全部工作——查收件箱、查任务池、认领、执行、汇报——都由 JChatMind 的
 * think-execute 循环驱动。LLM 在 think 阶段自主决定下一步动作，
 * 在 execute 阶段通过工具调用落地。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #start()} — 启动守护线程，调用 {@link JChatMind#run()}</li>
 *   <li>agent.run() 返回（LLM 主动 terminate 或异常退出）</li>
 *   <li>等待 {@link TeammateConfig#getRestartDelayMs()} 后重新启动 agent.run()</li>
 *   <li>{@link #stop()} — 设置停止标志并中断线程</li>
 * </ol>
 *
 * <h3>收件箱轮询</h3>
 * <p>收件箱轮询通过  在 Lead Agent 的 run() 循环末尾自动执行，
 * 将队友汇报以 {@link UserMessage} 形式注入 chatMemory。队友自身如需接收消息，
 * 则由 LLM 在 think 阶段调用 sendMessage 读取（或未来添加 readInbox 工具）。
 */
@Slf4j
public class TeammateWorker {

    /**
     * 队友的逻辑名称（如 "researcher", "coder"）
     */
    private final String name;

    /**
     * 队友使用的 Agent 配置 ID
     */
    private final String agentId;

    /**
     * 队友隔离的聊天会话 ID
     */
    private final String chatSessionId;

    /**
     * JChatMind 实例——think-execute 循环是队友的工作核心
     */
    private final JChatMind agent;

    /**
     * MessageBus——队友完成后向 Lead 汇报
     */
    private final MessageBus messageBus;

    /**
     * 聊天记忆——在 agent.run() 重启间共享
     */
    private final TokenAwareChatMemory chatMemory;

    /**
     * 队友配置
     */
    private final TeammateConfig config;

    /**
     * Lead Agent 名称（用于汇报时指定 target）
     */
    private final String leadName;

    /**
     * 运行标志，volatile 保证跨线程可见性
     */
    private volatile boolean running;
    /**
     * 本轮 agent.run() 返回后是否继续重启（ShutdownTool 可将其置为 false）
     */
    private volatile boolean shouldRestart = true;

    /**
     * 后台工作线程
     */
    private Thread workerThread;

    /**
     * 构造 TeammateWorker。
     *
     * @param name          队友逻辑名称
     * @param agentId       Agent 配置 ID
     * @param chatSessionId 隔离的聊天会话 ID
     * @param agent         JChatMind 实例
     * @param messageBus    MessageBus 实例
     * @param chatMemory    聊天记忆
     * @param config        队友配置
     * @param leadName      Lead Agent 名称
     */
    public TeammateWorker(String name,
                          String agentId,
                          String chatSessionId,
                          JChatMind agent,
                          MessageBus messageBus,
                          TokenAwareChatMemory chatMemory,
                          TeammateConfig config,
                          String leadName) {
        this.name = name;
        this.agentId = agentId;
        this.chatSessionId = chatSessionId;
        this.agent = agent;
        this.messageBus = messageBus;
        this.chatMemory = chatMemory;
        this.config = config;
        this.leadName = leadName;
    }

    /**
     * 启动后台守护线程。
     *
     * <p>线程以 {@code teammate-<name>} 命名，设置为守护线程，
     * 不会阻止 JVM 正常退出。
     */
    public void start() {
        if (running) {
            log.warn("Teammate '{}' 已在运行中，忽略重复启动", name);
            return;
        }
        running = true;
        workerThread = new Thread(this::runLoop, "teammate-" + name);
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Teammate '{}' 已启动 (thread={}, agentId={}, sessionId={})",
                name, workerThread.getName(), agentId, chatSessionId);
    }

    /**
     * 停止队友。
     *
     * <p>设置停止标志并中断工作线程。线程会在当前 sleep 或 agent.run() 结束后退出。
     */
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("Teammate '{}' 已发出停止信号", name);
    }

    // ==================== 内部方法 ====================

    /**
     * 队友主循环——JChatMind 的重启器。
     *
     * <p>循环逻辑：
     * <ol>
     *   <li>设置 ThreadLocal 上下文（供工具调用获取会话信息）</li>
     *   <li>调用 {@link JChatMind#run()} —— LLM 在 think-execute 循环中
     *       自主完成查收件箱→查任务池→认领→执行→汇报→继续或 terminate</li>
     *   <li>agent.run() 返回后，等待 restartDelayMs 后重新启动</li>
     *   <li>收到停止信号或线程中断时退出</li>
     * </ol>
     */
    private void runLoop() {
        log.info("Teammate '{}' 工作循环启动", name);
        while (running && shouldRestart) {
            // 设置 ThreadLocal 上下文，供工具调用访问会话和 Agent 信息
            AgentContextHolder.setLeadName(leadName);  // 让 replyToLead 知道目标
            try {
                // 每次启动 run 之前，确保状态机归零
                agent.resetForNextRun();
                // JChatMind 的 think-execute 循环直接驱动队友的全部工作
                agent.run();
                log.info("Teammate '{}' agent.run() 正常返回（LLM 主动 terminate）", name);
            } catch (Exception e) {
                log.error("Teammate '{}' agent.run() 异常退出，将在 {}ms 后重启",
                        name, config.getRestartDelayMs(), e);
            } finally {
                // 检查队友是否调用了 shutdown() → 标记本轮后不重启
                if (AgentContextHolder.needsShutdown()) {
                    shouldRestart = false;
                    log.info("Teammate '{}' 收到优雅停止信号，本轮结束后不再重启", name);
                }
                // 清理 ThreadLocal，防止内存泄漏
                AgentContextHolder.clearLeadName();
            }

            // 等待后重启，让队友持续工作
            if (running && shouldRestart) {
                try {
                    Thread.sleep(config.getRestartDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Teammate '{}' 在等待期间被中断，退出", name);
                    break;
                }
            }
        }
        log.info("Teammate '{}' 工作循环已退出 (shouldRestart={})", name, shouldRestart);
    }

    // ==================== Getter ====================

    public boolean isRunning() {
        return running;
    }

    public String getName() {
        return name;
    }

    public String getChatSessionId() {
        return chatSessionId;
    }

    public String getLeadName() {
        return leadName;
    }
}
