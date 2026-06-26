package com.kaer.agent.tools;

import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.kaer.agent.ConstantPrompt.TASK_DET_TASK_TEMPLATE;

/**
 * 获取任务详情工具 —— 根据任务 ID 查询任务的完整信息（含详细描述）
 *
 * <p>该工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认加载</p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class GetTaskTool implements Tool {

    private final TaskService taskService;

    @Override
    public String getName() {
        return "getTask";
    }

    @Override
    public String getDescription() {
        return "根据任务 ID 获取该任务的完整信息，包括详细描述、当前状态、负责人和依赖关系。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "getTask",
            description = "根据任务 ID 获取该任务的完整信息，包含长篇详细描述。用于深入了解某个具体任务的内容和要求。"
    )
    public String getTask(
            @org.springframework.ai.tool.annotation.ToolParam(description = "任务的唯一标识符 (ID)")
            String taskId) {

        try {
            if (taskId == null || taskId.isBlank()) {
                return buildError("参数错误: taskId（任务 ID）不能为空");
            }

            Task task = taskService.getTask(taskId);

            // 数据预处理：处理负责人和前置任务的缺省状态
            String ownerText = task.getOwner() != null ? task.getOwner() : "无";
            String blockedByText = (task.getBlockedBy() == null || task.getBlockedBy().isEmpty())
                    ? "无"
                    : String.join(", ", task.getBlockedBy());

            // 模板填充并返回
            return TASK_DET_TASK_TEMPLATE.formatted(
                    task.getId(),
                    task.getSubject(),
                    task.getStatus().getValue(),
                    ownerText,
                    blockedByText,
                    task.getDescription()
            );
        } catch (TaskNotFoundException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            log.error("[GetTaskTool] 获取任务失败: {}", e.getMessage(), e);
            return buildError("获取任务失败: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        log.warn("[GetTaskTool] {}", message);
        return message;
    }
}
