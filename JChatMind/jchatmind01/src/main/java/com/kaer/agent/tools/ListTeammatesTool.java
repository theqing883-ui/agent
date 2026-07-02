package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 列出当前会话下的所有活跃后台队友。
 *
 * <p>FIXED 类型，所有 Agent 默认可用。
 * 返回每个队友的名称和运行状态，供 Lead 了解当前有多少后台工人在执行任务。
 */
@Slf4j
@Component
public class ListTeammatesTool implements Tool {

    private final SpawnTeammateTool spawnTeammateTool;

    public ListTeammatesTool(SpawnTeammateTool spawnTeammateTool) {
        this.spawnTeammateTool = spawnTeammateTool;
    }

    @Override
    public String getName() {
        return "listTeammates";
    }

    @Override
    public String getDescription() {
        return "列出当前会话下所有活跃的后台队友及其运行状态。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 列出当前会话下所有后台队友。
     *
     * @return 格式化的队友列表
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "listTeammates",
            description = "列出当前会话下所有后台队友的名称和运行状态，用于了解可用的人力资源。"
    )
    public String listTeammates(
            @ToolParam(description = "可选，指定队友名称过滤（不填则列出全部）", required = false) String filterName) {

        String sessionId = AgentContextHolder.getSessionId();
        if (sessionId == null) {
            return "[查询失败] 无法获取当前会话上下文。";
        }

        var teammates = spawnTeammateTool.getTeammatesForSession(sessionId);

        if (teammates.isEmpty()) {
            return "当前会话没有活跃的后台队友。使用 spawnTeammate 可以启动新的队友。";
        }

        // 可选名称过滤
        var filtered = teammates;
        if (filterName != null && !filterName.isBlank()) {
            filtered = teammates.stream()
                    .filter(t -> t.name().toLowerCase().contains(filterName.toLowerCase()))
                    .toList();
        }

        if (filtered.isEmpty()) {
            return "当前会话没有匹配名称 '" + filterName + "' 的活跃队友。";
        }

        String result = filtered.stream()
                .map(t -> "- " + t.name() + " [" + (t.running() ? "运行中" : "已停止") + "]")
                .collect(Collectors.joining("\n"));

        return "当前会话的队友（共 " + filtered.size() + " 人）：\n" + result;
    }
}
