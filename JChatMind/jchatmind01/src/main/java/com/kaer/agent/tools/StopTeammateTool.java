package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 停止队友工具——Lead Agent 调用此工具立即终止指定后台队友。
 *
 * <p>FIXED 类型，队友创建时会被 TeammateConfig.excludedTools 排除（防止队友互相杀）。
 *
 * <p>这是硬停止：直接终止队友的守护线程。如果希望队友优雅退出（完成当前工作后停止），
 * 应先通过 sendMessage 发送 REQUEST 让队友自行调用 shutdown() + terminate。
 */
@Slf4j
@Component
public class StopTeammateTool implements Tool {

    private final SpawnTeammateTool spawnTeammateTool;

    public StopTeammateTool(SpawnTeammateTool spawnTeammateTool) {
        this.spawnTeammateTool = spawnTeammateTool;
    }

    @Override
    public String getName() {
        return "stopTeammate";
    }

    @Override
    public String getDescription() {
        return "立即停止指定名称的后台队友。队友线程会被终止。要优雅退出请先发 REQUEST 让队友调用 shutdown。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 停止指定队友。
     *
     * @param teammateName 要停止的队友名称
     * @return 操作结果
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "stopTeammate",
            description = """
                    立即停止指定名称的后台队友（硬停止）。
                    队友线程会被终止，且从当前会话的队友注册表中移除。
                    如果需要优雅退出（让队友完成当前工作后再停止），
                    请先通过 sendMessage 发送 REQUEST 给该队友，让它调用 shutdown() + terminate。"""
    )
    public String stopTeammate(
            @ToolParam(description = "要停止的队友名称") String teammateName) {

        String sessionId = AgentContextHolder.getSessionId();
        if (sessionId == null) {
            return "[停止失败] 无法获取当前会话上下文。";
        }
        if (teammateName == null || teammateName.isBlank()) {
            return "[停止失败] 队友名称不能为空。";
        }

        boolean stopped = spawnTeammateTool.stopTeammate(sessionId, teammateName);
        if (stopped) {
            log.info("Lead 已停止队友 '{}' (sessionId={})", teammateName, sessionId);
            return "队友 '" + teammateName + "' 已停止。";
        } else {
            return "[停止失败] 队友 '" + teammateName + "' 不存在或已停止。"
                    + "使用 listTeammates 查看当前活跃的队友。";
        }
    }
}
