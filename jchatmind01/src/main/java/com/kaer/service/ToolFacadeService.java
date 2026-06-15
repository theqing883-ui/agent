package com.kaer.service;

import com.kaer.agent.tools.Tool;

import java.util.List;

public interface ToolFacadeService {
    List<Tool> getAllTools();

    List<Tool> getOptionalTools();

    List<Tool> getFixedTools();

    /**
     * 获取所有工具，排除指定名称的工具。
     * 用于子 Agent 创建时过滤掉不应暴露的工具（如 delegateTask）。
     */
    List<Tool> getToolsExcluding(List<String> excludeNames);
}
