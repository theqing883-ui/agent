package com.kaer.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * MCP 工具管理器 —— 作为 MCP Client 连接第三方 MCP Server（文件系统、博查搜索、和风天气）。
 * <p>
 * 实现了统一的工具过滤、安全拦截以及相对路径解析逻辑。
 * 通过白名单机制限制可调用的 MCP 工具，并对文件操作进行路径安全校验，
 * 确保只能访问允许的基础目录（outputfiles）下的文件。
 * </p>
 *
 * @author Kaer
 * @version 1.0
 */
@Slf4j
@Component
public class McpToolManager implements Tool {

    /**
     * 允许访问的基础目录路径，所有文件操作都限制在此目录下
     */
    private static final String ALLOWED_BASE_PATH = "E:\\agent_project\\JChatMind\\jchatmind01\\data\\outputfiles";

    /**
     * 允许代理的底层 MCP 工具名称集合（白名单）
     * 包含博查联网搜索工具
     */
    private static final Set<String> ALLOWED_MCP_TOOL_NAMES = Set.of(
            // 博查联网搜索工具 (匹配 bocha-mcp.js 中的定义)
            "bocha_web_search");


    /**
     * JSON 对象映射器，用于工具调用参数的序列化和反序列化
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * MCP 工具映射表，键为工具名称，值为对应的 ToolCallback 实例
     * 仅包含白名单中允许的工具
     */
    private final Map<String, ToolCallback> mcpToolMap;

    /**
     * 构造函数：从 Spring 容器中自动装配所有 ToolCallbackProvider，
     * 并从中提取允许的 MCP 工具进行注册。
     *
     * @param providers ToolCallbackProvider 列表，每个 Provider 提供一组 MCP 工具
     */
    public McpToolManager(List<ToolCallbackProvider> providers) {
        List<ToolCallback> allTools = new ArrayList<>();
        for (ToolCallbackProvider provider : providers) {
            ToolCallback[] callbacks = provider.getToolCallbacks();
            log.info("McpToolManager 从 Provider [{}] 获取到 {} 个工具", provider.getClass().getSimpleName(), callbacks.length);
            for (ToolCallback cb : callbacks) {
                log.info("  MCP候选: name={}, desc={}", cb.getToolDefinition().name(), cb.getToolDefinition().description());
            }
            allTools.addAll(List.of(callbacks));
        }

        log.info("McpToolManager 从 {} 个 Provider 共收到 {} 个 MCP 工具", providers.size(), allTools.size());

        // 过滤并映射受支持的工具
        this.mcpToolMap = allTools.stream().filter(t -> {
            String name = t.getToolDefinition().name();
            // 兼容 Spring AI 自动添加的连接名前缀 (如 bocha-search_bocha_web_search)
            boolean matched = ALLOWED_MCP_TOOL_NAMES.contains(name);
            if (!matched) {
                matched = ALLOWED_MCP_TOOL_NAMES.stream().anyMatch(name::endsWith);
            }
            return matched;
        }).collect(Collectors.toMap(t -> t.getToolDefinition().name(), t -> t));
        log.info("MCP工具管理器初始化完成，共加载 {} 个受支持的 MCP 工具", mcpToolMap.size());
        mcpToolMap.keySet().forEach(name -> log.info(" -> 已注册工具: {}", name));
    }

    // ==================== Tool 接口实现 ====================

    /**
     * 获取工具名称
     *
     * @return 工具名称 "mcpManager"
     */
    @Override
    public String getName() {
        return "mcpManager";
    }

    /**
     * 获取工具描述
     *
     * @return 工具描述信息
     */
    @Override
    public String getDescription() {
        return "MCP工具集，提供博查联网搜索能力。";
    }

    /**
     * 获取工具类型
     *
     * @return ToolType.OPTIONAL（可选工具）
     */
    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    // ==================== 安全包装的 MCP 搜索工具 ====================

    /**
     * 实时联网搜索工具
     * 调用底层 MCP 的 bocha_web_search 工具进行搜索
     *
     * @param query 搜索关键词，不能为空
     * @return 搜索结果（JSON 格式字符串）
     */
    @org.springframework.ai.tool.annotation.Tool(name = "mcpWebSearch", description = "实时联网搜索。当询问最新新闻、实时资讯、事实校验或你不确定的知识时，使用此工具。")
    public String webSearch(@ToolParam(description = "搜索关键词") String query) {
        if (query == null || query.isBlank()) {
            return "Error: 搜索关键词不能为空";
        }
        // 调用底层 MCP 的 bocha_web_search 工具
        return callMcpTool("bocha_web_search", buildArgs("query", query));
    }

    /**
     * 构建单键值对参数的 JSON 字符串
     *
     * @param key   参数键
     * @param value 参数值
     * @return JSON 格式的参数字符串
     */
    private String buildArgs(String key, String value) {
        return toJson(Map.of(key, value));
    }

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param obj 待序列化的对象
     * @return JSON 字符串，序列化失败返回空对象 "{}"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return "{}";
        }
    }

    /**
     * 调用底层 MCP 工具
     * 支持完全匹配和后缀匹配两种方式查找工具
     *
     * @param toolName 工具名称
     * @param jsonArgs JSON 格式的参数
     * @return 工具执行结果或错误信息
     */
    private String callMcpTool(String toolName, String jsonArgs) {
        // 先尝试完全匹配，再尝试后缀匹配（兼容 Spring AI 添加的连接名前缀）
        ToolCallback tool = mcpToolMap.get(toolName);
        if (tool == null) {
            tool = mcpToolMap.values().stream()
                    .filter(t -> t.getToolDefinition().name().endsWith(toolName))
                    .findFirst()
                    .orElse(null);
        }

        // 工具不存在时返回错误信息
        if (tool == null) {
            return "Error: MCP工具 [" + toolName + "] 未就绪。可用工具列表: " + mcpToolMap.keySet();
        }

        // 执行工具调用
        log.info("[MCP安全层] 路由工具: {}, 最终参数: {}", tool.getToolDefinition().name(), jsonArgs);
        try {
            String result = tool.call(jsonArgs);
            log.info("[MCP安全层] 执行完成，返回长度: {}", result.length());
            return result;
        } catch (Exception e) {
            log.error("[MCP安全层] 执行失败", e);
            return "Error: MCP工具执行异常 - " + e.getMessage();
        }
    }
}