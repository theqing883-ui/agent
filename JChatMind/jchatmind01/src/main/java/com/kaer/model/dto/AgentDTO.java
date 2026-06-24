package com.kaer.model.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能代理(Agent)数据传输对象
 * <p>用于封装智能代理的配置信息，包括模型类型、工具权限、知识库权限等</p>
 */
@Data
@Builder/*是 Lombok 库提供的一个注解，用于自动生成构建器模式（Builder Pattern）的代码。*/
public class AgentDTO {

    /**
     * 代理唯一标识
     */
    private String id;

    /**
     * 代理名称
     */
    private String name;

    /**
     * 代理描述信息
     */
    private String description;

    /**
     * 系统提示词，用于引导代理行为
     */
    private String systemPrompt;

    /**
     * 模型类型
     */
    private ModelType model;

    /**
     * 允许使用的工具列表
     */
    private List<String> allowedTools;

    /**
     * 允许访问的知识库列表
     */
    private List<String> allowedKbs;

    /**
     * 聊天配置选项
     */
    private ChatOptions chatOptions;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 模型类型枚举
     * <p>定义支持的AI模型类型</p>
     */
    @Getter
    @AllArgsConstructor
    public enum ModelType {

        /**
         * DeepSeek聊天模型
         */
        DEEPSEEK_CHAT("deepseek-chat"),

        /**
         * GLM-4.6模型
         */
        GLM_4_6("glm-4.6");

        /**
         * 模型名称标识
         */
        @JsonValue
        private final String modelName;

        /**
         * 根据模型名称获取对应的枚举值
         *
         * @param modelName 模型名称
         * @return ModelType 对应的枚举值
         * @throws IllegalArgumentException 当模型名称未知时抛出
         */
        public static ModelType fromModelName(String modelName) {
            for (ModelType type : ModelType.values()) {
                if (type.modelName.equals(modelName)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown model type: " + modelName);
        }
    }

    /**
     * 聊天配置选项内部类
     * <p>用于配置AI聊天的参数，如温度、topP、上下文窗口等</p>
     */
    @Data
    @AllArgsConstructor
    @Builder
    public static class ChatOptions {

        /** 默认温度值 */
        private static final Double DEFAULT_TEMPERATURE = 0.7;
        /** 默认消息长度 */
        private static final Integer DEFAULT_MESSAGE_LENGTH = 10;
        /** 默认Top-P值 */
        private static final Double DEFAULT_TOP_P = 1.0;
        /** 默认最大上下文token数 */
        private static final Integer DEFAULT_MAX_CONTEXT_TOKENS = 128000;
        /** 默认系统提示词预留token数 */
        private static final Integer DEFAULT_SYSTEM_PROMPT_RESERVE_TOKENS = 2000;
        /** 默认工具定义预留token数 */
        private static final Integer DEFAULT_TOOL_DEFINITIONS_RESERVE_TOKENS = 3000;
        /** 默认对话历史token预算 */
        private static final Integer DEFAULT_RECENT_MESSAGES_TOKEN_BUDGET = 60000;
        /** 默认工具响应最大token数 */
        private static final Integer DEFAULT_MAX_TOOL_RESPONSE_TOKENS = 4000;
        /** 默认是否启用对话摘要 */
        private static final Boolean DEFAULT_SUMMARIZATION_ENABLED = true;
        /** 默认摘要阈值 */
        private static final Double DEFAULT_SUMMARIZATION_THRESHOLD = 0.7;
        /** 默认是否启用记忆笔记 */
        private static final Boolean DEFAULT_MEMORY_NOTE_ENABLED = true;
        /** 默认记忆笔记间隔轮数 */
        private static final Integer DEFAULT_MEMORY_NOTE_INTERVAL_TURNS = 8;
        // ===== 子 Agent 委派配置 =====
        /** 默认子 Agent 最大步数 */
        private static final Integer DEFAULT_CHILD_MAX_STEPS = 10;
        /** 默认子 Agent 排除的工具 */
        private static final List<String> DEFAULT_CHILD_EXCLUDED_TOOLS = List.of("delegateTask");
        /** 默认 Max Output Tokens（null = 使用模型默认值） */
        private static final Integer DEFAULT_MAX_OUTPUT_TOKENS = 8000;
        /** 默认单次最大输出 token 上限（用于截断后无痕升级） */
        private static final Integer DEFAULT_UPGRADED_MAX_TOKENS = 64000;

        /**
         * 温度参数，控制输出的随机性
         * <p>值越高（接近1.0），输出越随机、多样化；值越低（接近0），输出越确定、保守</p>
         * <p>默认值：0.7</p>
         */
        private Double temperature;

        /**
         * Top-P参数，控制核采样概率
         * <p>限制只考虑累积概率达到P的最可能token集合，用于平衡输出多样性和质量</p>
         * <p>默认值：1.0（不限制）</p>
         */
        private Double topP;

        /**
         * 聊天消息窗口长度，即保留的历史消息数量
         */
        private Integer messageLength;

        // ===== 上下文窗口工程新字段 =====
        /** 总上下文窗口 token 上限 */
        private Integer maxContextTokens;
        /** 系统提示词预留 token 数 */
        private Integer systemPromptReserveTokens;
        /** 工具定义预留 token 数 */
        private Integer toolDefinitionsReserveTokens;
        /** 对话历史 token 预算 */
        private Integer recentMessagesTokenBudget;
        /** 单条工具响应最大 token 数 */
        private Integer maxToolResponseTokens;
        /** 是否启用对话摘要 */
        private Boolean summarizationEnabled;
        /** 触发摘要的 token 使用率阈值 */
        private Double summarizationThreshold;
        /** 是否启用记忆笔记 */
        private Boolean memoryNoteEnabled;
        /** 每隔多少轮生成一次记忆笔记 */
        private Integer memoryNoteIntervalTurns;

        // ===== 子 Agent 委派配置 =====
        /** 子 Agent 最大 think-execute 步数 */
        private Integer childMaxSteps;
        /** 子 Agent 中排除的工具名称列表 */
        private List<String> childExcludedTools;
        /** 子 Agent 的系统提示词覆盖 */
        private String childSystemPrompt;

        // ===== 韧性引擎配置 =====
        /** 单次请求最大输出 token 数，被截断后由 ErrorRecoveryEngine 自动升级 */
        private Integer maxOutputTokens;
        /** 备胎模型名称（当前模型不可用时自动切换），如 "glm-4.6" */
        private String fallbackModel;

        // ===== 工具响应全局缓存配置 =====
        /** 是否启用工具响应全局缓存（关闭时退化为纯硬截断） */
        private Boolean toolCacheEnabled;
        /** 触发缓存的 token 阈值（超过此值的工具响应将被缓存），默认 4000 */
        private Integer toolCacheTriggerTokens;
        /** 截断后展示给 LLM 的 token 数，默认 2000 */
        private Integer toolCacheShownTokens;

        /**
         * 创建默认的聊天配置选项
         * <p>使用预定义的默认值构建ChatOptions实例，包含推荐的上下文窗口配置</p>
         *
         * @return ChatOptions 包含默认配置的聊天选项实例
         */
        public static ChatOptions defaultTokenOptions() { // 不包含 temperature、Top-P等
            return ChatOptions.builder()
                    .maxContextTokens(DEFAULT_MAX_CONTEXT_TOKENS)
                    .systemPromptReserveTokens(DEFAULT_SYSTEM_PROMPT_RESERVE_TOKENS)
                    .toolDefinitionsReserveTokens(DEFAULT_TOOL_DEFINITIONS_RESERVE_TOKENS)
                    .recentMessagesTokenBudget(DEFAULT_RECENT_MESSAGES_TOKEN_BUDGET)
                    .maxToolResponseTokens(DEFAULT_MAX_TOOL_RESPONSE_TOKENS)
                    .summarizationEnabled(DEFAULT_SUMMARIZATION_ENABLED)
                    .summarizationThreshold(DEFAULT_SUMMARIZATION_THRESHOLD)
                    .memoryNoteEnabled(DEFAULT_MEMORY_NOTE_ENABLED)
                    .memoryNoteIntervalTurns(DEFAULT_MEMORY_NOTE_INTERVAL_TURNS)
                    .childMaxSteps(DEFAULT_CHILD_MAX_STEPS)
                    .childExcludedTools(DEFAULT_CHILD_EXCLUDED_TOOLS)
                    .childSystemPrompt(null) // null = 由 DelegationTool 使用静态默认值
                    .maxOutputTokens(DEFAULT_MAX_OUTPUT_TOKENS)
                    .fallbackModel(null)
                    .toolCacheEnabled(null)       // null = 使用全局配置
                    .toolCacheTriggerTokens(null)  // null = 使用全局默认 4000
                    .toolCacheShownTokens(null)    // null = 使用全局默认 2000
                    .build();
        }
        public static ChatOptions defaultOptions() {
            return ChatOptions.builder()
                    .temperature(DEFAULT_TEMPERATURE)
                    .topP(DEFAULT_TOP_P)
                    .messageLength(DEFAULT_MESSAGE_LENGTH)
                    .maxContextTokens(DEFAULT_MAX_CONTEXT_TOKENS)
                    .systemPromptReserveTokens(DEFAULT_SYSTEM_PROMPT_RESERVE_TOKENS)
                    .toolDefinitionsReserveTokens(DEFAULT_TOOL_DEFINITIONS_RESERVE_TOKENS)
                    .recentMessagesTokenBudget(DEFAULT_RECENT_MESSAGES_TOKEN_BUDGET)
                    .maxToolResponseTokens(DEFAULT_MAX_TOOL_RESPONSE_TOKENS)
                    .summarizationEnabled(DEFAULT_SUMMARIZATION_ENABLED)
                    .summarizationThreshold(DEFAULT_SUMMARIZATION_THRESHOLD)
                    .memoryNoteEnabled(DEFAULT_MEMORY_NOTE_ENABLED)
                    .memoryNoteIntervalTurns(DEFAULT_MEMORY_NOTE_INTERVAL_TURNS)
                    .childMaxSteps(DEFAULT_CHILD_MAX_STEPS)
                    .childExcludedTools(DEFAULT_CHILD_EXCLUDED_TOOLS)
                    .childSystemPrompt(null)
                    .maxOutputTokens(DEFAULT_MAX_OUTPUT_TOKENS)
                    .fallbackModel(null)
                    .toolCacheEnabled(null)       // null = 使用全局配置
                    .toolCacheTriggerTokens(null)  // null = 使用全局默认 4000
                    .toolCacheShownTokens(null)    // null = 使用全局默认 2000
                    .build();
        }
    }
}