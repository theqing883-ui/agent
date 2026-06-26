package com.kaer.agent.tools;

import com.kaer.model.vo.TaskSummary;
import com.kaer.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 列出所有任务工具 —— 以摘要形式展示所有任务（不包含长篇描述，节省 Token）
 *
 * <p>该工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认加载</p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ListTaskTool implements Tool {

    private final TaskService taskService;

    @Override
    public String getName() {
        return "listTask";
    }

    @Override
    public String getDescription() {
        return "列出系统中的所有任务摘要（按需使用以节省 Token）。返回每个任务的 ID、主题、状态、负责人及依赖关系。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "listTask",
            description = "列出系统中的所有任务摘要。返回每个任务的 ID、主题、状态、负责人及依赖关系，故意省略长篇描述以节省 Token。如需查看某个任务的完整描述，请使用 getTask。"
    )
    public String listTask() {

        try {
            List<TaskSummary> tasks = taskService.listTasks();

            if (tasks.isEmpty()) {
                return "当前没有任何任务。可以使用 createTask 创建新任务。";
            }

            StringBuilder result = new StringBuilder();
            result.append("任务列表（共 ").append(tasks.size()).append(" 个）\n");
            result.append("═══════════════════════════════════\n\n");

            // 按状态分组显示
            List<TaskSummary> pending = tasks.stream()
                    .filter(t -> t.getStatus() != null && "pending".equals(t.getStatus().getValue()))
                    .toList();
            List<TaskSummary> inProgress = tasks.stream()
                    .filter(t -> t.getStatus() != null && "in_progress".equals(t.getStatus().getValue()))
                    .toList();
            List<TaskSummary> completed = tasks.stream()
                    .filter(t -> t.getStatus() != null && "completed".equals(t.getStatus().getValue()))
                    .toList();

            if (!pending.isEmpty()) {
                result.append(" 待处理:\n");
                for (TaskSummary t : pending) {
                    appendTaskSummary(result, t, "  ");
                }
                result.append("\n");
            }

            if (!inProgress.isEmpty()) {
                result.append(" 进行中:\n");
                for (TaskSummary t : inProgress) {
                    appendTaskSummary(result, t, "  ");
                }
                result.append("\n");
            }

            if (!completed.isEmpty()) {
                result.append(" 已完成:\n");
                for (TaskSummary t : completed) {
                    appendTaskSummary(result, t, "  ");
                }
                result.append("\n");
            }

            result.append("═══════════════════════════════════\n");
            result.append("提示: 使用 getTask <taskId> 查看任务详细描述\n");
            result.append("      使用 claimTask <taskId> <agentName> 认领可执行的任务\n");

            return result.toString();
        } catch (Exception e) {
            log.error("[ListTaskTool] 获取任务列表失败: {}", e.getMessage(), e);
            return buildError("获取任务列表失败: " + e.getMessage());
        }
    }

    private void appendTaskSummary(StringBuilder sb, TaskSummary t, String prefix) {
        sb.append(prefix).append("• [").append(t.getId()).append("] ");
        sb.append(t.getSubject());
        if (t.getOwner() != null) {
            sb.append(" (负责人: ").append(t.getOwner()).append(")");
        }
        if (t.getBlockedBy() != null && !t.getBlockedBy().isEmpty()) {
            sb.append(" 依赖: [").append(String.join(", ", t.getBlockedBy())).append("]");
        }
        sb.append("\n");
    }

    private String buildError(String message) {
        log.warn("[ListTaskTool] {}", message);
        return message;
    }
}
