package com.kaer.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.agent.messagebus.MessageBus;
import com.kaer.agent.skill.SkillManager;
import com.kaer.agent.skill.SkillMeta;
import com.kaer.agent.tools.Tool;
import com.kaer.config.ChatClientRegistry;
import com.kaer.context.manager.ContextWindowManager;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.converter.AgentConverter;
import com.kaer.converter.ChatMessageConverter;
import com.kaer.converter.KnowledgeBaseConverter;
import com.kaer.mapper.AgentMapper;
import com.kaer.mapper.KnowledgeBaseMapper;
import com.kaer.model.dto.AgentDTO;
import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.dto.KnowledgeBaseDTO;
import com.kaer.model.entity.Agent;
import com.kaer.model.entity.KnowledgeBase;
import com.kaer.resilience.ErrorRecoveryEngine;
import com.kaer.service.ChatMessageFacadeService;
import com.kaer.service.SseService;
import com.kaer.service.ToolFacadeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * JChatMind 工厂类
 * <p>
 * 负责创建 {@link JChatMind} 实例，是 Agent 运行时的核心工厂。
 * 该类协调多个组件来构建完整的聊天代理，包括：
 * - 加载 Agent 配置
 * - 加载聊天历史（记忆）
 * - 解析运行时工具和知识库
 * - 构建工具回调
 * </p>
 */
@Component
public class JChatMindFactory {
    private static final Logger log = LoggerFactory.getLogger(JChatMindFactory.class);

    /**
     * ChatClient 注册中心，用于根据模型名称获取对应的 ChatClient 实例
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * SSE 服务，用于向客户端推送实时消息
     */
    private final SseService sseService;

    /**
     * Agent 数据访问层，用于从数据库加载 Agent 配置
     */
    private final AgentMapper agentMapper;

    /**
     * Agent 转换器，用于 Entity 和 DTO 之间的转换
     */
    private final AgentConverter agentConverter;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseConverter knowledgeBaseConverter;

    /**
     * 工具门面服务，用于获取可用的工具列表
     */
    private final ToolFacadeService toolFacadeService;

    /**
     * 聊天消息门面服务，用于获取聊天历史记录
     */
    private final ChatMessageFacadeService chatMessageFacadeService;

    /**
     * 聊天消息转换器，用于消息格式转换
     */
    private final ChatMessageConverter chatMessageConverter;

    /**
     * 上下文窗口管理器
     */
    private final ContextWindowManager contextWindowManager;

    private final TokenAwareChatMemory chatMemory;
    private final SkillManager skillManager;
    private final ErrorRecoveryEngine errorRecoveryEngine;

    /**
     * 消息总线（可选注入，用于多 Agent 协作时的收件箱轮询）。
     * 当 Spring 容器中存在 MessageBus Bean 时自动注入，否则为 null。
     */
    @Autowired(required = false)
    private MessageBus messageBus;

    /**
     * 运行时 Agent 配置（线程局部变量，用于构建过程中的临时存储）
     */
    private AgentDTO agentConfig;

    /**
     * 构造函数，依赖注入所需的服务组件
     */
    public JChatMindFactory(
            ChatClientRegistry chatClientRegistry,
            SseService sseService,
            AgentMapper agentMapper,
            AgentConverter agentConverter,
            ToolFacadeService toolFacadeService,
            ChatMessageFacadeService chatMessageFacadeService,
            ChatMessageConverter chatMessageConverter,
            ContextWindowManager contextWindowManager,
            TokenAwareChatMemory tokenAwareChatMemory,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeBaseConverter knowledgeBaseConverter,
            SkillManager skillManager,
            ErrorRecoveryEngine errorRecoveryEngine
    ) {
        this.chatClientRegistry = chatClientRegistry;
        this.sseService = sseService;
        this.agentMapper = agentMapper;
        this.agentConverter = agentConverter;
        this.toolFacadeService = toolFacadeService;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;
        this.contextWindowManager = contextWindowManager;
        this.chatMemory = tokenAwareChatMemory;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseConverter = knowledgeBaseConverter;
        this.skillManager = skillManager;
        this.errorRecoveryEngine = errorRecoveryEngine;
    }

    /**
     * 创建 JChatMind 实例（工厂核心方法）
     *
     * @param agentId       Agent 的唯一标识
     * @param chatSessionId 聊天会话的唯一标识
     * @return 构建完成的 JChatMind 实例（当前返回 null，待实现完整逻辑）
     */
    public JChatMind create(String agentId, String chatSessionId) {
        // 1. 从数据库加载 Agent 配置
        Agent agent = loadAgent(agentId);
        // 2. 转换为运行时配置 DTO
        agentConfig = toAgentConfig(agent);
        log.debug("agentConfig: {}", agentConfig);
        // 3. 加载聊天历史作为记忆
        List<Message> memory = loadMemory(chatSessionId);

        // 4. 解析运行时知识库
        List<KnowledgeBaseDTO> knowledgeBase = resolveRuntimeKnowledgeBases(agentConfig);
        // 5. 解析运行时工具
        List<Tool> tools = resolveRuntimeTools(agentConfig);
        // 6. 构建工具回调
        List<ToolCallback> toolCallbacks = buildToolCallbacks(tools);
        // 7. 调用 buildAgentRuntime 组装完整实例
        return buildAgentRuntime(agent, memory, knowledgeBase, toolCallbacks, chatSessionId, null);
    }

    /**
     * 创建用于任务委派的子 Agent 实例。
     * <p>
     * 与 {@link #create(String, String)} 的区别：
     * <ul>
     *   <li>可指定最大步数（子 Agent 通常步数更少）</li>
     *   <li>可排除特定工具（防止子 Agent 递归委派）</li>
     *   <li>可覆盖系统提示词（子 Agent 使用专注执行的 CHILD_SYSTEM_PROMPT）</li>
     * </ul>
     *
     * @param agentId                   Agent 唯一标识
     * @param chatSessionId             子会话唯一标识
     * @param excludeToolNames          要排除的工具名称列表（如 "delegateTask"）
     * @param childSystemPromptOverride 子 Agent 系统提示词覆盖（null 则使用 Agent 原始配置）
     * @return 构建完成的子 JChatMind 实例
     */
    public JChatMind createForDelegation(
            String agentId,
            String chatSessionId,
            List<String> excludeToolNames,
            String childSystemPromptOverride
    ) {
        // 1. 从数据库加载 Agent 配置
        Agent agent = loadAgent(agentId);
        // 2. 转换为运行时配置 DTO
        agentConfig = toAgentConfig(agent);
        log.debug("createForDelegation agentId={}, sessionId={}, excludeTools={}",
                agentId, chatSessionId, excludeToolNames);

        // 3. 加载聊天历史作为记忆（子会话仅含任务指令的 USER 消息）
        List<Message> memory = loadMemory(chatSessionId);

        // 4. 解析运行时知识库
        List<KnowledgeBaseDTO> knowledgeBase = resolveRuntimeKnowledgeBases(agentConfig);
        // 5. 解析运行时工具，并过滤掉要排除的工具
        List<Tool> tools = resolveRuntimeTools(agentConfig);
        if (excludeToolNames != null && !excludeToolNames.isEmpty()) {
            tools = tools.stream()
                    .filter(t -> !excludeToolNames.contains(t.getName()))
                    .collect(Collectors.toList());
        }

        // 6. 构建工具回调
        List<ToolCallback> toolCallbacks = buildToolCallbacks(tools);

        // 7. 组装子 Agent 实例（传入系统提示词覆盖）

        return buildAgentRuntime(agent, memory, knowledgeBase, toolCallbacks,
                chatSessionId, childSystemPromptOverride);
    }

    /**
     * 构建 Agent 运行时实例
     * <p>
     * 将所有组件组装成完整的 JChatMind 实例，是创建流程的最后一步。
     *
     * @param systemPromptOverride 系统提示词覆盖（null 则使用 Agent 的默认提示词）
     */
    private JChatMind buildAgentRuntime(
            Agent agent,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBase,
            List<ToolCallback> toolCallbacks,
            String chatSessionId,
            String systemPromptOverride
    ) {
        ChatClient chatClient = chatClientRegistry.get(agent.getModel());
        if (chatClient == null) {
            throw new IllegalArgumentException("未配置 ChatClient：" + agent.getModel());
        }
        // 先清除旧记忆再加载，避免每次 create() 调用时历史消息被重复追加
        chatMemory.clear(chatSessionId);
        chatMemory.add(chatSessionId, memory);

        // 使用覆盖的系统提示词，如果没有则使用 Agent 的默认提示词
        String effectivePrompt = systemPromptOverride != null
                ? systemPromptOverride
                : agent.getSystemPrompt();
        if (effectivePrompt != null && !effectivePrompt.isEmpty()) {
            chatMemory.add(chatSessionId,
                    new SystemMessage(effectivePrompt));
        }

        // 获取可用技能列表（启动时扫描的全局注册表）
        List<SkillMeta> availableSkills = skillManager.getAllSkillMetas();

        JChatMind jChatMind = new JChatMind(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getSystemPrompt(),
                chatClient,
                chatMemory,
                agentConfig.getChatOptions(),
                toolCallbacks,
                knowledgeBase,
                availableSkills,
                chatSessionId,
                sseService,
                chatMessageFacadeService,
                chatMessageConverter,
                contextWindowManager,
                errorRecoveryEngine,
                agent.getModel()
        );

        // 如果 MessageBus 可用，注入到 Agent 中以启用收件箱轮询
        if (messageBus != null) {
            jChatMind.setMessageBus(messageBus);
        }

        return jChatMind;
    }

    /**
     * 加载 Agent 配置
     * <p>
     * 从数据库根据 AgentId 查询 Agent 实体。
     */
    private Agent loadAgent(String agentId) {
        return agentMapper.selectById(agentId);
    }

    /**
     * 将 Agent 实体转换为运行时配置 DTO
     * <p>
     * 包含 JSON 序列化处理，将数据库中的配置转换为运行时可用的 DTO。
     */
    private AgentDTO toAgentConfig(Agent agent) {
        try {
            // 1. 将数据库实体转为 DTO
            AgentDTO agentDTO = agentConverter.toDTO(agent);
            // TODO :前端设置 长下文窗口工程相关的配置，现在设置默认值
            AgentDTO.ChatOptions options = agentDTO.getChatOptions();
            // 2. 填充上下文窗口工程的默认配置 (只在前端未传值时才使用默认值，保留前端自定义的权利)
            if (options == null) {
                agentDTO.setChatOptions(AgentDTO.ChatOptions.defaultOptions());
            } else {
                AgentDTO.ChatOptions mergedOptions = AgentDTO.ChatOptions.defaultTokenOptions();
                // 将前端自定义的值覆盖到底板上
                mergedOptions.setTopP(options.getTopP());
                mergedOptions.setTemperature(options.getTemperature());
                mergedOptions.setMessageLength(options.getMessageLength());
                // 将合并后的新配置塞回 DTO 中，替换掉原来的配置
                agentDTO.setChatOptions(mergedOptions);
            }
            return agentDTO;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析 Agent 配置失败", e);
        }
    }

    /**
     * 加载聊天历史作为记忆，加载全部历史消息（token 预算由 ContextWindowManager 管理）。
     */
    private List<Message> loadMemory(String chatSessionId) {
        // 获取配置的消息长度限制
        Integer length = agentConfig.getChatOptions().getMessageLength();
        // 从服务获取最近的聊天消息
        List<ChatMessageDTO> chatMessageDTOS = chatMessageFacadeService
                .getChatMessagesBySessionIdRecently(chatSessionId, length);
        ArrayList<Message> memory = new ArrayList<>();

        // 遍历消息并按角色类型转换
        for (ChatMessageDTO chatMessageDTO : chatMessageDTOS) {
            switch (chatMessageDTO.getRole()) {
                case SYSTEM:
                    if (!StringUtils.hasLength(chatMessageDTO.getContent())) {
                        continue;
                    }
                    // 系统消息插入到列表头部
                    memory.add(0, new SystemMessage(chatMessageDTO.getContent()));
                    break;
                case USER:
                    if (!StringUtils.hasLength(chatMessageDTO.getContent())) {
                        continue;
                    }
                    memory.add(new UserMessage(chatMessageDTO.getContent()));
                    break;
                case ASSISTANT: {
                    boolean hasToolCalls = chatMessageDTO.getMetadata() != null
                            && chatMessageDTO.getMetadata().getToolCalls() != null
                            && !chatMessageDTO.getMetadata().getToolCalls().isEmpty();
                    if (!hasToolCalls && !StringUtils.hasLength(chatMessageDTO.getContent())) {
                        continue;
                    }
                    memory.add(AssistantMessage.builder()
                            .toolCalls(chatMessageDTO.getMetadata() != null
                                    ? chatMessageDTO.getMetadata().getToolCalls() : null)
                            .content(chatMessageDTO.getContent() != null
                                    ? chatMessageDTO.getContent() : "")
                            .build());
                    break;
                }
                case TOOL: {
                    ToolResponseMessage.ToolResponse toolResponse = chatMessageDTO.getMetadata() != null
                            ? chatMessageDTO.getMetadata().getToolResponse() : null;
                    if (toolResponse == null) {
                        continue;
                    }
                    memory.add(ToolResponseMessage.builder()
                            .responses(List.of(toolResponse))
                            .build());
                    break;
                }
                default:
                    log.error("未知角色: {},content: {}", chatMessageDTO.getRole().getRole(),
                            chatMessageDTO.getContent());
            }
        }
        return memory;
    }

    /**
     * 解析运行时知识库
     * <p>
     * 根据 Agent 配置的 allowedKbs 列表，解析并加载对应的知识库。
     * 当前版本返回空列表，待后续实现知识库功能。
     */
    private List<KnowledgeBaseDTO> resolveRuntimeKnowledgeBases(AgentDTO agentConfig) {
        List<String> allowedKbIds = agentConfig.getAllowedKbs();
        if (allowedKbIds == null || allowedKbIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectByIdBatch(allowedKbIds);
        if (knowledgeBases.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeBaseDTO> kbDTOs = new ArrayList<>();
        try {
            for (KnowledgeBase knowledgeBase : knowledgeBases) {
                KnowledgeBaseDTO kbDTO = knowledgeBaseConverter.toDTO(knowledgeBase);
                kbDTOs.add(kbDTO);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return kbDTOs;
    }

    /**
     * 解析 Agent 运行时可用的工具列表
     *
     * <p>工具分为两类：
     * <ul>
     *   <li>固定工具（Fixed）：所有 Agent 都默认拥有的工具</li>
     *   <li>可选工具（Optional）：根据 Agent 配置按需添加的工具</li>
     * </ul>
     *
     * @param agentConfig Agent 配置对象，包含允许使用的可选工具列表
     * @return Agent 运行时可用的完整工具列表
     */
    private List<Tool> resolveRuntimeTools(AgentDTO agentConfig) {
        // 1. 获取所有固定工具作为基础工具列表
        List<Tool> runtimeTools = new ArrayList<>(toolFacadeService.getFixedTools());

        // 2. 获取 Agent 配置中允许使用的可选工具列表
        List<String> allowedTools = agentConfig.getAllowedTools();

        // 如果没有配置可选工具，则直接返回固定工具列表
        if (allowedTools == null || allowedTools.isEmpty()) {
            return runtimeTools;
        }

        // 3. 将可选工具列表转换为 Map（工具名称 → 工具对象），便于快速查找
        Map<String, Tool> optionalToolMap = toolFacadeService.getOptionalTools()
                // 2. 将集合转换为 Stream 流，开启链式处理模式
                .stream()
                // 3. 执行终止操作 collect，将流中的元素收集到 Map 容器中
                .collect(Collectors.toMap(
                        /*
                         * 参数一：Key Mapper（键映射器）
                         * 使用方法引用 Tool::getName。
                         * 逻辑：提取 Tool 对象的 name 属性作为 Map 的 Key（键）。
                         */
                        Tool::getName,

                        /*
                         * 参数二：Value Mapper（值映射器）
                         * 使用 Function.identity()。
                         * 逻辑：输入什么就返回什么，即将 Tool 对象本身作为 Map 的 Value（值）。
                         */
                        Function.identity()
                ));

        // 4. 根据配置的允许列表，将对应的可选工具添加到运行时工具列表
        for (String allowedTool : allowedTools) {
            Tool tool = optionalToolMap.get(allowedTool);
            if (tool != null) {
                runtimeTools.add(tool);
            }
        }

        return runtimeTools;
    }

    /**
     * 构建工具回调列表
     * <p>
     * 将工具列表转换为 Spring AI 可识别的 ToolCallback 列表，
     * 便于在聊天过程中调用工具。
     */
    private List<ToolCallback> buildToolCallbacks(List<Tool> tools) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Tool tool : tools) {
            // 解析工具的真实目标对象（处理 AOP 代理情况）
            Object target = resolveToolTarget(tool);
            // 使用 Spring AI 的工具回调提供者构建回调
            ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(target)
                    .build()
                    .getToolCallbacks();
            callbacks.addAll(Arrays.asList(toolCallbacks));
        }
        return callbacks;
    }

    /**
     * 解析工具目标对象
     * <p>
     * 处理 AOP 代理情况，获取工具的真实类对象，确保反射调用正常工作。
     */
    private Object resolveToolTarget(Tool tool) {
        try {
            return AopUtils.isAopProxy(tool)
                    ? AopUtils.getTargetClass(tool)
                    : tool;
        } catch (Exception e) {
            throw new IllegalStateException("解析工具目标失败 " + tool.getName() + " 失败", e);
        }
    }
}