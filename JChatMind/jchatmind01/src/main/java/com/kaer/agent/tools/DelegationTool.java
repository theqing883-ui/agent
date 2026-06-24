package com.kaer.agent.tools;

import com.kaer.agent.*;
import com.kaer.mapper.AgentMapper;
import com.kaer.mapper.ChatSessionMapper;
import com.kaer.message.SseMessage;
import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.entity.Agent;
import com.kaer.model.entity.ChatSession;
import com.kaer.service.ChatMessageFacadeService;
import com.kaer.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.kaer.agent.AgentRoleConstants.CHILD;

/**
 * 任务委派工具——主 Agent 调用此工具将子任务委派给独立的子 Agent 执行。
 *
 * <p>执行流程：
 * <ol>
 *   <li>创建子会话（关联父会话的 parentSessionId）</li>
 *   <li>持久化任务指令为子会话的 USER 消息</li>
 *   <li>通过工厂创建子 Agent（隔离上下文、裁剪工具、缩减步数）</li>
 *   <li>同步运行子 Agent 直至完成</li>
 *   <li>提取子 Agent 的精简结论，丢弃中间执行过程</li>
 *   <li>返回结论给主 Agent</li>
 * </ol>
 *
 * <p>此工具为 FIXED 类型，所有 Agent 默认可用，但子 Agent 创建时会被排除，
 * 从而防止无限递归委派。
 */
@Slf4j
@Component
public class DelegationTool implements com.kaer.agent.tools.Tool {

    private static final String TOOL_NAME = "delegateTask";

    private final JChatMindFactory jChatMindFactory;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final SseService sseService;
    private final DelegationConfig delegationConfig;
    private final AgentMapper agentMapper;

    public DelegationTool( @Lazy JChatMindFactory jChatMindFactory,// 解决循环依赖
                          ChatSessionMapper chatSessionMapper,
                          ChatMessageFacadeService chatMessageFacadeService,
                          SseService sseService,
                          DelegationConfig delegationConfig,
                          AgentMapper agentMapper) {
        this.jChatMindFactory = jChatMindFactory;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.sseService = sseService;
        this.delegationConfig = delegationConfig;
        this.agentMapper = agentMapper;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "将独立子任务委派给子智能体执行。子智能体会在隔离的上下文环境中专注工作，执行完毕后返回精简结论。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 委派任务给子 Agent 执行 delegateTask：委派任务。
     *
     * @param taskDescription 清晰、完整地描述要执行的具体任务
     * @param childAgentId    子智能体 ID（可选，默认复用主 Agent）
     * @return 子 Agent 执行后的精简结论
     */
    @org.springframework.ai.tool.annotation.Tool(name = "delegateTask", description = """
            当你需要执行一个独立、聚焦的子任务时（如搜索信息、查询数据库、分析文档等），
            使用此工具将任务委派给子智能体。子智能体会专注执行，完成后返回精简结论。
            参数说明：
            - taskDescription：清晰描述要执行的任务，包括所有必要的上下文和具体要求
            - childAgentId：可选，指定执行任务的智能体ID；不填则使用当前智能体""")
    public String delegateTask(
            @ToolParam(description = "清晰、完整地描述要执行的具体任务，包括所有必要的上下文和参数") String taskDescription,
            @ToolParam(description = "目标执行智能体 ID（Agent ID）。默认继承父级会话的智能体实例；仅当需指定特定能力的智能体处理子任务时显式传入", required = false) String childAgentId) {

        // 从 ThreadLocal 获取父 Agent 上下文
        String parentSessionId = AgentContextHolder.getSessionId();
        String parentAgentId = AgentContextHolder.getAgentId();

        if (parentSessionId == null || parentAgentId == null) {
            return "[委派失败] 无法获取父 Agent 上下文，请确保 Agent 已正确初始化。";
        }

        // 确定子 Agent ID
        String effectiveChildAgentId = (childAgentId != null && !childAgentId.isBlank())
                ? childAgentId : parentAgentId;
        log.info("DelegationTool: parentSession={}, childAgent={}, task={}",
                parentSessionId, effectiveChildAgentId,
                taskDescription.substring(0, Math.min(100, taskDescription.length())));

        // 1. 创建子会话
        String childSessionId;
        try {
            LocalDateTime now = LocalDateTime.now();
            ChatSession childSession = ChatSession.builder()
                    .agentId(effectiveChildAgentId)
                    .parentSessionId(parentSessionId)
                    .sessionType(CHILD)
                    .title("子任务: " + (taskDescription.length() > 50
                            ? taskDescription.substring(0, 50) : taskDescription))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            chatSessionMapper.insert(childSession);
            childSessionId = childSession.getId();
            log.info("子会话已创建: childSessionId={}", childSessionId);
        } catch (Exception e) {
            log.error("创建子会话失败", e);
            return "[委派失败] 无法创建子任务会话: " + e.getMessage();
        }

        // 2. 持久化任务指令到子会话
        try {
            ChatMessageDTO taskMessage = ChatMessageDTO.builder()
                    .sessionId(childSessionId)
                    .role(ChatMessageDTO.RoleType.USER)
                    .content(taskDescription)
                    .build();
            chatMessageFacadeService.createChatMessage(taskMessage);
        } catch (Exception e) {
            log.error("持久化子任务消息失败", e);
            return "[委派失败] 无法写入子任务消息: " + e.getMessage();
        }

        // 3. 发送 SSE 通知父会话，直接推送到前端了
        try {
            sseService.send(parentSessionId,
                    SseMessage.builder()
                            .type(SseMessage.Type.AI_EXECUTING)
                            .payload(SseMessage.Payload.builder()
                                    .statusText("委派子任务执行中: " + (taskDescription.length() > 80
                                            ? taskDescription.substring(0, 80) + "..." : taskDescription))
                                    .build())
                            .build());
        } catch (Exception e) {
            log.warn("发送 SSE 委派通知失败（不影响主流程）", e);
        }

        // 4. 解析子 Agent 配置
        int childMaxSteps = delegationConfig.getChildMaxSteps();
        List<String> excludedTools = delegationConfig.getExcludedTools();
        String childSystemPrompt =ConstantPrompt.CHILD_SYSTEM_PROMPT;

        try {
            Agent childAgent = agentMapper.selectById(effectiveChildAgentId);
            if (childAgent != null) {
                // 从 Agent 配置中读取 ChatOptions，如有定制则覆盖全局默认
                // 注意：这里直接读取数据库原始配置做简单解析，避免引入完整 DTO 解析链
                // 如果将来需要更精细的配置能力，可以扩展 AgentDTO.ChatOptions
            }
        } catch (Exception e) {
            log.warn("解析子 Agent 配置失败，使用全局默认值", e);
        }

        // 5. 创建并运行子 Agent
        JChatMind childAgent;
        try {
            childAgent = jChatMindFactory.createForDelegation(
                    effectiveChildAgentId,
                    childSessionId,
                    childMaxSteps,
                    excludedTools,
                    childSystemPrompt
            );
        } catch (Exception e) {
            log.error("创建子 Agent 失败", e);
            return "[委派失败] 无法创建子智能体: " + e.getMessage();
        }

        // 设置子 Agent 的线程上下文
        try {
            AgentContextHolder.set(childSessionId, effectiveChildAgentId);
            childAgent.run();
        } catch (Exception e) {
            log.error("子 Agent 运行异常", e);
            return "[委派失败] 子智能体运行异常: " + e.getMessage();
        } finally {
            // 恢复父 Agent 上下文
            AgentContextHolder.set(parentSessionId, parentAgentId);
        }

        // 6. 提取子 Agent 结论
        String result = extractChildResult(childAgent);

        // 7. 持久化结论到子会话（标记为 ASSISTANT 消息）
        try {
            ChatMessageDTO conclusionMessage = ChatMessageDTO.builder()
                    .sessionId(childSessionId)
                    .role(ChatMessageDTO.RoleType.ASSISTANT)
                    .content("[任务执行结论]\n" + result)
                    .build();
            chatMessageFacadeService.createChatMessage(conclusionMessage);
        } catch (Exception e) {
            log.warn("持久化子任务结论失败（不影响主流程）", e);
        }

        log.info("DelegationTool 完成: childSessionId={}, resultLength={}", childSessionId, result.length());
        return result;
    }

    /**
     * 从子 Agent 的聊天记忆中提取最终结论。
     * <p>
     * 倒序遍历消息列表，取最后一条有实质内容的 AssistantMessage，
     * 丢弃中间的 tool-call / tool-response 等执行过程噪音。
     *
     * @param childAgent 已完成运行的子 Agent
     * @return 精简后的结论文本
     */
    private String extractChildResult(JChatMind childAgent) {
        AgentState state = childAgent.getAgentState();

        if (state == AgentState.ERROR) {
            return "[子任务执行失败] 子智能体运行过程中发生错误，请检查日志后重试。";
        }

        List<Message> allMessages = childAgent.getChatMemory()
                .getAll(childAgent.getChatSessionId());

        // 倒序查找最后一条有内容的 AssistantMessage
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            Message msg = allMessages.get(i);
            if (msg instanceof AssistantMessage assistantMsg) {
                String text = assistantMsg.getText();
                if (text != null && !text.isBlank()) {
                    return truncateResult(text);
                }
            }
        }

        // 如果没找到 AssistantMessage，检查是否达到了最大步数
        if (state == AgentState.FINISHED) {
            return "[子任务达到最大步骤限制] 未能获取到明确的结论内容。";
        }

        return "[子任务执行完毕] 未获取到明确的结论内容。";
    }

    /**
     * 截断过长的结果。
     */
    private String truncateResult(String text) {
        int maxLen = delegationConfig.getMaxResultLength();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...\n[结果已截断，完整内容请查看子会话记录]";
    }

}
