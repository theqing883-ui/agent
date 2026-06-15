package com.kaer.agent;

import com.kaer.context.manager.ContextWindowManager;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.model.ContextWindow;
import com.kaer.context.truncator.ContextTruncator;
import com.kaer.converter.ChatMessageConverter;
import com.kaer.message.SseMessage;
import com.kaer.model.dto.AgentDTO;
import com.kaer.model.dto.ChatMessageDTO;
import com.kaer.model.dto.KnowledgeBaseDTO;
import com.kaer.model.response.CreateChatMessageResponse;
import com.kaer.model.vo.ChatMessageVO;
import com.kaer.service.ChatMessageFacadeService;
import com.kaer.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class JChatMind {
    // AI 返回的，已经持久化，但是需要 sse 发给前端的消息
    private final List<ChatMessageDTO> pendingChatMessages = new ArrayList<>();
    // 默认最多循环次数（可由 setter 覆盖）
    private int maxSteps = 20;
    // 智能体 ID
    private String agentId;
    // 名称
    private String name;
    // 描述
    private String description;
    // 默认系统提示词
    private String systemPrompt;
    // 交互实例
    private ChatClient chatClient;
    // 状态
    private AgentState agentState;
    // 可用的工具
    private List<ToolCallback> availableTools;
    // 可访问的知识库
    private List<KnowledgeBaseDTO> availableKbs;
    // 工具调用管理器
    private ToolCallingManager toolCallingManager;
    // Token 感知的聊天记忆（替代 MessageWindowChatMemory）
    private TokenAwareChatMemory chatMemory;
    // 模型的聊天会话 ID
    private String chatSessionId;
    // 子任务模型的聊天会话 ID
//    private String childChatSessionId;

    // ChatOptions（含上下文窗口配置）
    private AgentDTO.ChatOptions agentChatOptions;
    // SSE 服务
    private SseService sseService;
    private ChatMessageConverter chatMessageConverter;
    private ChatMessageFacadeService chatMessageFacadeService;
    // 最后一次的 ChatResponse
    private ChatResponse lastChatResponse;
    // 上下文窗口管理器
    private ContextWindowManager contextWindowManager;
    // 工具响应截断器
    private ContextTruncator contextTruncator;

    public JChatMind() {
    }

    public JChatMind(String agentId, String name, String description, String systemPrompt,
                     ChatClient chatClient,
                     TokenAwareChatMemory chatMemory,
                     AgentDTO.ChatOptions agentChatOptions,
                     List<ToolCallback> availableTools, List<KnowledgeBaseDTO> availableKbs,
                     String chatSessionId, SseService sseService,
                     ChatMessageFacadeService chatMessageFacadeService,
                     ChatMessageConverter chatMessageConverter,
                     ContextWindowManager contextWindowManager,
                     ContextTruncator contextTruncator
    ) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.agentChatOptions = agentChatOptions;
        this.availableTools = availableTools;
        this.availableKbs = availableKbs;
        this.chatSessionId = chatSessionId;
        this.sseService = sseService;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;
        this.contextWindowManager = contextWindowManager;
        this.contextTruncator = contextTruncator;
        this.agentState = AgentState.IDLE;
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    /**
     * 启动 Agent 的主循环
     * Agent 会在以下条件之一满足时停止：
     * 1. 达到最大循环次数（MAX_STEPS = 20）
     * 2. AI 决定结束对话（agentState 变为 FINISHED）
     * 3. 发生异常（agentState 变为 ERROR）
     *
     * @throws IllegalStateException 当 Agent 状态不是 IDLE 时抛出
     * @throws RuntimeException      当执行过程中发生异常时抛出
     */
    public void run() {
        // 校验 Agent 状态必须为 IDLE（空闲）
        if (agentState != AgentState.IDLE) {
            throw new IllegalStateException("Agent 状态不为 idle(空闲) ");
        }

        try {
            // 设置 Agent 上下文（用于 DelegationTool 获取父会话信息）
            AgentContextHolder.set(this.chatSessionId, this.agentId);

            // Agent 主循环：最多执行 maxSteps 次，或直到状态变为 FINISHED
            for (int i = 0; i < this.maxSteps && agentState != AgentState.FINISHED; i++) {
                // 当前步骤编号（从1开始）
                int currentStep = i + 1;

                // 执行单步：思考（think）+ 执行（execute）
                step();

                // 检查是否达到最大步骤限制
                if (currentStep >= this.maxSteps) {
                    agentState = AgentState.FINISHED;
                    log.warn("Agent {} reached max steps of {}", agentId, this.maxSteps);
                }
            }

            // 循环正常结束，设置状态为 FINISHED
            agentState = AgentState.FINISHED;
        } catch (Exception e) {
            // 捕获异常，设置状态为 ERROR
            agentState = AgentState.ERROR;
            log.error("Error running agent", e);
            throw new RuntimeException("Error running agent", e);
        } finally {
            // 清除 Agent 上下文，防止 ThreadLocal 内存泄漏
            AgentContextHolder.clear();
        }
    }

    private void step() {
        // 检查是否需要生成记忆笔记
        int noteInterval = agentChatOptions.getMemoryNoteIntervalTurns() != null
                ? agentChatOptions.getMemoryNoteIntervalTurns() : 1;
        Boolean noteEnabled = agentChatOptions.getMemoryNoteEnabled();
        if (noteEnabled != null && noteEnabled
                && contextWindowManager.shouldGenerateNote(chatSessionId, noteInterval)) {
            contextWindowManager.generateMemoryNote(chatSessionId);
        }

        if (think()) {
            execute();
        } else {
            this.agentState = AgentState.FINISHED;
        }
    }


    /**
     * Agent 的核心「思考」方法，负责与 AI 模型进行对话并决定下一步动作。
     *
     * <p>执行流程：
     * 1. 构建思考提示词，包含当前可用知识库信息
     * 2. 通过上下文窗口管理器构建会话上下文
     * 3. 调用 AI 模型获取响应
     * 4. 解析工具调用指令
     * 5. 保存响应消息并更新内存
     * 6. 根据是否存在工具调用决定返回值
     *
     * <p>关键设计：
     * - 使用 ContextWindow 管理上下文窗口，确保输入 token 数量在模型限制内
     * - 禁用内部工具执行（internalToolExecutionEnabled=false），由 Agent 自行控制工具调用
     * - 将工具回调函数注册到聊天客户端，允许 AI 选择调用合适的工具
     *
     * @return 如果 AI 决定调用工具则返回 true（需要继续执行），否则返回 false（对话结束）
     */
    private boolean think() {
        // 构建思考阶段的系统提示词，告知 AI 当前可用的知识库资源
        String thinkPrompt = SystemPrompt.THINK_SYSTEM_PROMPT.formatted(this.availableKbs);

        // 构建上下文窗口，整合会话历史、工具列表和提示词
        ContextWindow ctxWindow = contextWindowManager.buildContextWindow(
                chatSessionId, chatMemory, availableTools, thinkPrompt, agentChatOptions);

        // 配置聊天选项：禁用内部工具执行，由 Agent 手动处理
        ChatOptions chatOpts = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false).build();

        // 构建完整的 Prompt 对象
        Prompt prompt = Prompt.builder()
                .chatOptions(chatOpts)
                .messages(ctxWindow.selectedMessages())
                .build();

        // 调用 AI 模型获取响应，注册工具回调函数
        this.lastChatResponse = this.chatClient.prompt(prompt)
                .system(thinkPrompt)
                .toolCallbacks(this.availableTools)
                .call()
                .chatClientResponse()
                .chatResponse();

        // 确保响应不为空
        Assert.notNull(this.lastChatResponse, "AI 聊天响应为空");

        // 提取 AI 输出消息和工具调用列表
        AssistantMessage output = this.lastChatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        // 保存消息到持久化存储
        saveMessage(output);
        // 将输出添加到聊天内存
        chatMemory.add(chatSessionId, output);
        // 刷新待处理的聊天消息列表
        refreshPendingChatMessages();
        // 记录工具调用日志
        logToolCalls(toolCalls);

        // 返回是否存在工具调用：true 表示需要执行工具，false 表示对话结束
        return !toolCalls.isEmpty();
    }

    /**
     * Agent 的「执行」方法，负责执行 AI 决定调用的工具
     */
    private void execute() {
        Assert.notNull(this.lastChatResponse, "AI 聊天响应为空");

        if (!this.lastChatResponse.hasToolCalls()) {
            return;
        }

        ChatOptions chatOpts = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .toolCallbacks(this.availableTools)
                .build();

        // 排除 think() 添加的最后一条 AssistantMessage，
        // 因为 executeToolCalls 已通过 lastChatResponse 持有它，buildConversationHistoryAfterToolExecution
        // 不做去重会导致 AssistantMessage(带 tool_calls) 在 conversationHistory 中出现两次，
        // 进而触发 API 校验: assistant message with tool_calls must be followed by tool messages
        List<Message> messages = new ArrayList<>(this.chatMemory.getAll(this.chatSessionId));
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof AssistantMessage) {
            messages.remove(messages.size() - 1);
        }

        Prompt prompt = Prompt.builder()
                .chatOptions(chatOpts)
                .messages(messages)
                .build();

        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, lastChatResponse);

        this.chatMemory.replace(this.chatSessionId, toolExecutionResult.conversationHistory());

        // 从工具执行结果中提取最后一条工具响应消息
        // 工具执行完成后，conversationHistory 的最后一条消息即为工具响应
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult
                .conversationHistory()
                .get(toolExecutionResult.conversationHistory().size() - 1);

        String collect = toolResponseMessage.getResponses()
                .stream()
                .map(resp -> "工具" + resp.name() + "的返回结果为：" + resp.responseData())
                .collect(Collectors.joining("\n"));
        log.info("调用工具结果：{}", collect);

        // 保存工具调用结果
        saveMessage(toolResponseMessage);
        // 将新消息推送给前端（通过 SSE）
        refreshPendingChatMessages();

        if (toolResponseMessage.getResponses()
                .stream()
                .anyMatch(resp -> resp.name().contains("terminate"))) {
            this.agentState = AgentState.FINISHED;
            log.info("任务完成");
        }
    }

    /**
     * 将 AI 请求的工具调用（Tool Calls）列表格式化并输出到日志中。
     *
     * @param toolCalls 大模型返回的工具调用指令列表
     */
    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {

        // 如果 AI 这一轮没有要求调用任何工具，直接记录一条简单日志并退出方法，避免空指针异常。
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("\n\n[ToolCalling] 无工具调用");
            return;
        }

        // 2. 【流式处理】使用 Java 8 Stream API 进行数据加工：
        // 我们不使用普通的 for 循环，而是使用 IntStream 来处理索引，这样方便给工具调用编号。
        String collect = IntStream.range(0, toolCalls.size()) // 生成一个 0 到 size-1 的整数流
                .mapToObj(i -> {
                    // 根据当前的索引 i，从原始列表中获取具体的工具调用对象
                    AssistantMessage.ToolCall toolCall = toolCalls.get(i);

                    // 3. 【字符串格式化】：
                    // 使用 String.format 构建多行文本，增强可读性：
                    // %d 代表整数（序号），从 1 开始计数
                    // %s 代表字符串（工具名和 JSON 格式的参数）
                    return String.format(
                            "[ToolCalling #%d]\n- name      : %s\n- arguments : %s",
                            i + 1,               // 序号展示给人类看，所以加 1
                            toolCall.name(),      // 获取工具名称（如 "databaseQuery"）
                            toolCall.arguments()  // 获取工具参数（通常是 JSON 字符串）
                    );
                })
                // 4. 【聚合操作】：
                // 将流中每一条格式化好的字符串，使用“换行符(\n)”作为分隔符，拼接成一整个大字符串。
                .collect(Collectors.joining("\n"));

        // 5. 【日志输出】：
        log.info("\n\n[ToolCalling] 工具调用结果：{}", collect);
    }

    // 发送消息到前端
    private void refreshPendingChatMessages() {
        for (ChatMessageDTO message : this.pendingChatMessages) {
            ChatMessageVO chatMessageVO = chatMessageConverter.toVO(message);
            SseMessage sseMessage = SseMessage.builder().type(SseMessage.Type.AI_GENERATED_CONTENT)
                    .payload(SseMessage.Payload.builder()
                            .message(chatMessageVO).build())
                    .metadata(SseMessage.Metadata.builder()
                            .chatMessageId(message.getId())
                            .build()).build();
            try {
                sseService.send(this.chatSessionId, sseMessage);
            } catch (Exception e) {
                // 如果是子 Agent，这里必然报错，但我们只打印 debug 日志，不阻断程序运行
                log.debug("无法发送 SSE 消息至会话 {} (可能是子 Agent 正在静默运行): {}",
                        this.chatSessionId, e.getMessage());
            }
        }
        pendingChatMessages.clear();
    }

    // 需要 Agent 持久化的 Message 子类有以下两类
    // AssistantMessage //AI 生成的消息
    // ToolResponseMessage //工具调用后的相应结果
    // AI生成的消息和工具调用后的相应结果都会返回到Spring AI框架中，我们持久化的消息都是从spring ai框架中获取的
    // SystemMessage 不需要持久化
    // UserMessage 在每次用户发送问题之间就已经持久化过了
    private void saveMessage(Message message) {
        int maxToolResp = agentChatOptions.getMaxToolResponseTokens() != null
                ? agentChatOptions.getMaxToolResponseTokens() : 4000;
        Message processed = contextTruncator.truncateSingleToolResponse(message, maxToolResp);

        ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder();

        if (processed instanceof AssistantMessage assistantMessage) {
            ChatMessageDTO chatMessageDTO = builder.role(ChatMessageDTO.RoleType.ASSISTANT)
                    .content(assistantMessage.getText())
                    .sessionId(this.chatSessionId)
                    .metadata(ChatMessageDTO.MetaData.builder()
                            .toolCalls(assistantMessage.getToolCalls())
                            .build())
                    .build();
            CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
            chatMessageDTO.setId(chatMessage.getChatMessageId());
            pendingChatMessages.add(chatMessageDTO);
        } else if (processed instanceof ToolResponseMessage toolResponseMessage) {
            List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
            for (ToolResponseMessage.ToolResponse toolResponse : toolResponses) {
                ChatMessageDTO chatMessageDTO = builder.role(ChatMessageDTO.RoleType.TOOL)
                        .content(toolResponse.responseData())
                        .sessionId(this.chatSessionId)
                        .metadata(ChatMessageDTO.MetaData.builder()
                                .toolResponse(toolResponse).build())
                        .build();
                CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
                chatMessageDTO.setId(chatMessage.getChatMessageId());
                pendingChatMessages.add(chatMessageDTO);
            }
        } else {
            throw new IllegalArgumentException("不支持的 Message 类型: " + message.getClass().getName());
        }
    }


    // ===== 多 Agent 委派支持 =====

    /**
     * 设置最大 think-execute 循环次数（用于子 Agent 的缩减步数）。
     */
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /**
     * 获取当前 Agent 状态。
     */
    public AgentState getAgentState() {
        return agentState;
    }

    /**
     * 获取 Token 感知的聊天记忆（用于委派时提取子 Agent 结论）。
     */
    public TokenAwareChatMemory getChatMemory() {
        return chatMemory;
    }

    /**
     * 获取当前会话 ID（用于委派场景下外部访问子 Agent 的会话）。
     */
    public String getChatSessionId() {
        return chatSessionId;
    }

    @Override
    public String toString() {
        return "JChatMind {" + "name = " + name + ",\n"
                + "description = " + description + ",\n"
                + "agentId = " + agentId + ",\n"
                + "systemPrompt = " + systemPrompt + "}";
    }

}