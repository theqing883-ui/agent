package com.kaer.service.impl;

import com.kaer.agent.tools.Tool;
import com.kaer.agent.tools.ToolType;
import com.kaer.service.ToolFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具门面服务实现类
 * <p>
 * 负责管理和提供 Agent 可用的工具列表。
 * 通过 Spring 自动注入所有实现了 {@link Tool} 接口的 Bean，
 * 并支持按工具类型进行分类查询。
 */
@Service
@AllArgsConstructor
public class ToolFacadeServiceImpl implements ToolFacadeService {

    /**
     * 所有已注册的工具列表
     * <p>
     * Spring 容器会自动扫描并注入所有实现了 {@link Tool} 接口且被 Spring 管理的 Bean。
     * 这是一种依赖注入的集合注入特性。
     */
    private final List<Tool> tools;

    /**
     * 获取可选工具列表
     * <p>
     * 可选工具是指 Agent 可以选择性使用的工具，由 Agent 配置决定是否启用。
     *
     * @return 可选工具列表
     */
    @Override
    public List<Tool> getOptionalTools() {
        return getToolsByType(ToolType.OPTIONAL);
    }

    /**
     * 获取固定工具列表
     * <p>
     * 固定工具是指所有 Agent 默认都会加载的工具，无需额外配置。
     *
     * @return 固定工具列表
     */
    @Override
    public List<Tool> getFixedTools() {
        return getToolsByType(ToolType.FIXED);
    }

    /**
     * 获取所有工具列表
     * <p>
     * 返回系统中注册的所有工具，包括可选工具和固定工具。
     *
     * @return 所有已注册的工具列表
     */
    @Override
    public List<Tool> getAllTools() {
        return tools;
    }

    /**
     * 按工具类型过滤工具列表
     * <p>
     * 使用 Stream API 根据工具类型进行过滤，返回匹配的工具列表。
     *
     * @param type 工具类型（OPTIONAL 或 FIXED）
     * @return 匹配指定类型的工具列表
     */
    private List<Tool> getToolsByType(ToolType type) {
//        System.out.println(this.tools);
        return tools.stream()
                .filter(tool -> tool.getType() == type)  // 按类型过滤
                .collect(Collectors.toList());            // 收集结果
    }
}