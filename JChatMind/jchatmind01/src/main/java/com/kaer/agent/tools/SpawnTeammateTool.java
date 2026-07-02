package com.kaer.agent.tools;

import com.kaer.agent.*;
import com.kaer.agent.messagebus.MessageBus;
import com.kaer.config.TeammateConfig;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.mapper.ChatSessionMapper;
import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.entity.ChatSession;
import com.kaer.service.ChatMessageFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.kaer.agent.ConstantPrompt.TEAMMATE_SPAWN_TEAMMATE_PROMPT;

/**
 * 唤醒队友工具——Lead Agent 调用此工具启动一个后台队友线程。
 *
 * <p>队友在独立的守护线程中运行，通过 JChatMind 的 think-execute 循环自主：
 * <ol>
 *   <li>检查收件箱获取 Lead 的直接指令</li>
 *   <li>轮询任务系统发现 PENDING 任务</li>
 *   <li>认领 → 执行 → 完成 → 向 Lead 汇报</li>
 *   <li>当无任务可做时 terminate，由 TeammateWorker 自动重启</li>
 * </ol>
 *
 * <p>此工具为 FIXED 类型，但队友创建时会被 TeammateConfig.excludedTools 排除，
 * 防止队友无限唤醒子队友。
 *
 * <p>参考 {@link DelegationTool} 的子会话创建和 JChatMind 初始化模式。
 */
@Slf4j
@Component
public class SpawnTeammateTool implements Tool {

    private final JChatMindFactory factory;
    private final MessageBus messageBus;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final TeammateConfig teammateConfig;
    private final TokenAwareChatMemory chatMemory;

    /**
     * 按父会话隔离的队友注册表。
     *
     * <p>外层 key 为父会话 ID，内层 key 为队友名称。
     * 使用 ConcurrentHashMap 保证并发安全。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, TeammateWorker>>
            sessionTeammates = new ConcurrentHashMap<>();

    public SpawnTeammateTool(
            @Lazy JChatMindFactory factory,
            MessageBus messageBus,
            ChatSessionMapper chatSessionMapper,
            ChatMessageFacadeService chatMessageFacadeService,
            TeammateConfig teammateConfig,
            TokenAwareChatMemory chatMemory) {
        this.factory = factory;
        this.messageBus = messageBus;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.teammateConfig = teammateConfig;
        this.chatMemory = chatMemory;
    }

    @Override
    public String getName() {
        return "spawnTeammate";
    }

    @Override
    public String getDescription() {
        return "启动一个后台队友 Agent。队友在独立线程中运行，自动从任务系统拉取 PENDING 任务并执行，完成后向 Lead 汇报。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 唤醒后台队友。
     *
     * @param teammateName       队友的唯一名称（如 "researcher", "coder"）
     * @param agentId            队友使用的 Agent 配置 ID（可选，默认复用当前 Agent）
     * @param customSystemPrompt 自定义系统提示词（可选，不填则使用默认队友提示词）
     * @return 启动结果描述
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "spawnTeammate",
            description = """
                    启动一个后台队友 Agent。队友会在独立守护线程中持续运行，自动从任务系统拉取
                    并执行 PENDING 任务，完成后通过 MessageBus 向你汇报。
                    参数说明：
                    - teammateName：队友的唯一名称
                    - agentId：可选，指定队友使用的 Agent 配置 ID
                    - customSystemPrompt：可选，自定义队友的系统提示词"""
    )
    public String spawnTeammate(
            @ToolParam(description = "队友的唯一名称（如 'researcher', 'coder'）") String teammateName,
            @ToolParam(description = "队友使用的 Agent 配置 ID（可选，不填则复用当前 Agent）", required = false) String agentId,
            @ToolParam(description = "自定义系统提示词（可选，不填则使用默认队友提示词）", required = false) String customSystemPrompt) {

        // 1. 获取父会话上下文
        String parentSessionId = AgentContextHolder.getSessionId();
        String parentAgentId = AgentContextHolder.getAgentId();

        if (parentSessionId == null || parentAgentId == null) {
            return "[启动失败] 无法获取当前 Agent 的会话上下文，请确保 Agent 已正确初始化。";
        }

        // 参数校验
        if (teammateName == null || teammateName.isBlank()) {
            return "[启动失败] 队友名称不能为空。";
        }

        // 2. 确定有效的 Agent ID 和 System Prompt
        String effectiveAgentId = (agentId != null && !agentId.isBlank()) ? agentId : parentAgentId;
        String effectivePrompt = (customSystemPrompt != null && !customSystemPrompt.isBlank())
                ? customSystemPrompt
                : ConstantPrompt.TEAMMATE_SYSTEM_PROMPT;

        log.info("SpawnTeammateTool: 启动队友 '{}' (agentId={}, sessionId={})",
                teammateName, effectiveAgentId, parentSessionId);

        // 3. 防止同会话重复唤醒同名人
        ConcurrentHashMap<String, TeammateWorker> sessionWorkers =
                sessionTeammates.computeIfAbsent(parentSessionId, k -> new ConcurrentHashMap<>());
        if (sessionWorkers.containsKey(teammateName)) {
            TeammateWorker existing = sessionWorkers.get(teammateName);
            if (existing != null && existing.isRunning()) {
                return "[失败] 队友 '" + teammateName + "' 已在本会话中运行。如需重启，请等待其自行终止后重试。";
            }
            // 如果已存在但已停止，移除旧记录，允许重新创建
            sessionWorkers.remove(teammateName);
        }

        // 3.5. 容量检查：每个父会话最多允许 N 个队友
        long runningCount = sessionWorkers.values().stream()
                .filter(TeammateWorker::isRunning).count();
        if (runningCount >= teammateConfig.getMaxTeammates()) {
            return String.format(
                    "[失败] 当前会话已达到队友数量上限（%d/%d）。"
                            + "请等待已有队友空闲（terminate）后再创建。"
                            + "使用 listTeammates 查看当前队友状态。",
                    runningCount, teammateConfig.getMaxTeammates());
        }

        // 4. 创建子会话
        String childSessionId;
        try {
            LocalDateTime now = LocalDateTime.now();
            ChatSession childSession = ChatSession.builder()
                    .agentId(effectiveAgentId)
                    .parentSessionId(parentSessionId)
                    .sessionType(AgentRoleConstants.TEAMMATE)
                    .title("Teammate: " + teammateName)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            chatSessionMapper.insert(childSession);
            childSessionId = childSession.getId();
            log.info("Teammate 子会话已创建: childSessionId={}", childSessionId);
        } catch (Exception e) {
            log.error("创建 Teammate 子会话失败", e);
            return "[启动失败] 无法创建子会话: " + e.getMessage();
        }

        // 5. 持久化初始用户消息（作为 Teammate 启动后的第一轮触发）
        try {
            ChatMessageDTO initMessage = ChatMessageDTO.builder()
                    .sessionId(childSessionId)
                    .role(ChatMessageDTO.RoleType.USER)
                    .content("你已被启动为后台队友 '" + teammateName + "'。"
                            + "请开始检查任务系统（调用 listTask），寻找你可以处理的 PENDING 任务。"
                            + "如果暂时没有可执行的任务，请调用 terminate。")
                    .build();
            chatMessageFacadeService.createChatMessage(initMessage);
        } catch (Exception e) {
            log.error("持久化 Teammate 初始消息失败", e);
            return "[启动失败] 无法写入初始任务消息: " + e.getMessage();
        }

        // 6. 创建 JChatMind 实例（使用自定义 SystemPrompt）
        JChatMind agent;
        try {
            agent = factory.createForDelegation(
                    effectiveAgentId,
                    childSessionId,
                    teammateConfig.getExcludedTools(),
                    effectivePrompt
            );
        } catch (Exception e) {
            log.error("创建 Teammate JChatMind 失败", e);
            return "[启动失败] 无法创建队友 Agent 实例: " + e.getMessage();
        }

        // 7. 创建并启动 TeammateWorker
        TeammateWorker worker = new TeammateWorker(
                teammateName,
                effectiveAgentId,
                childSessionId,
                agent,
                messageBus,
                chatMemory,
                teammateConfig,
                parentAgentId
        );
        worker.start();
        sessionWorkers.put(teammateName, worker);

        log.info("SpawnTeammateTool 完成: teammateName={}, sessionId={}, promptLength={}",
                teammateName, childSessionId, effectivePrompt.length());

        return TEAMMATE_SPAWN_TEAMMATE_PROMPT.formatted(teammateName, effectiveAgentId, childSessionId);
    }

    /**
     * 关闭指定会话下的所有队友。
     *
     * <p>通常在 Lead Agent 完成时调用，清理后台线程。
     *
     * @param sessionId 父会话 ID
     */
    public void shutdownSession(String sessionId) {
        ConcurrentHashMap<String, TeammateWorker> workers = sessionTeammates.remove(sessionId);
        if (workers != null && !workers.isEmpty()) {
            workers.values().forEach(TeammateWorker::stop);
            log.info("已关闭会话 {} 下的 {} 个队友", sessionId, workers.size());
        }
    }

    /**
     * 停止指定会话下的单个队友。
     *
     * @param sessionId    父会话 ID
     * @param teammateName 队友名称
     * @return true 表示成功找到并停止，false 表示队友不存在
     */
    public boolean stopTeammate(String sessionId, String teammateName) {
        ConcurrentHashMap<String, TeammateWorker> workers = sessionTeammates.get(sessionId);
        if (workers == null) return false;
        TeammateWorker worker = workers.remove(teammateName);
        if (worker != null && worker.isRunning()) {
            worker.stop();
            log.info("已停止队友 '{}' (sessionId={})", teammateName, sessionId);
            return true;
        }
        return false;
    }


    /**
     * 获取指定会话下所有队友的名称和运行状态。
     *
     * @param sessionId 父会话 ID
     * @return 队友信息列表
     */
    public List<TeammateInfo> getTeammatesForSession(String sessionId) {
        ConcurrentHashMap<String, TeammateWorker> workers = sessionTeammates.get(sessionId);
        if (workers == null) return List.of();
        return workers.entrySet().stream()
                .map(e -> new TeammateInfo(e.getKey(), e.getValue().isRunning()))
                .toList();
    }

    /**
     * 队友简要信息。
     */
    public record TeammateInfo(String name, boolean running) {
    }
}
