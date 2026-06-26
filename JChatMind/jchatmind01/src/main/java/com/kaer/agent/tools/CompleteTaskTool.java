package com.kaer.agent.tools;

import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.kaer.agent.ConstantPrompt.COMPLETE_TASK_TEMPLATE;

/**
 * 完成任务工具 —— Agent 将已认领的任务标记为已完成
 *
 * <p>该工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认加载</p>
 *
 * <p>完成约束：
 * <ol>
 *   <li>任务当前状态必须为 in_progress</li>
 *   <li>调用者必须与任务的 owner 匹配（只能完成自己认领的任务）</li>
 * </ol>
 */
@Slf4j
@Component
@AllArgsConstructor
public class CompleteTaskTool implements Tool {

    private final TaskService taskService;

    @Override
    public String getName() {
        return "completeTask";
    }

    @Override
    public String getDescription() {
        return "将已认领的任务标记为已完成。只能完成自己认领的任务（owner 必须匹配），完成后会解除依赖此任务的其他任务的阻塞。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "completeTask",
            description = "将已认领的任务标记为已完成。只能完成自己认领的任务（owner 必须匹配），完成后会解除依赖此任务的其他任务的阻塞状态。"
    )
    public String completeTask(
            @org.springframework.ai.tool.annotation.ToolParam(description = "要完成的任务的唯一标识符 (ID)")
            String taskId,
            @org.springframework.ai.tool.annotation.ToolParam(description = "完成该任务的 Agent 名称（必须与认领时的 Agent 名称一致）")
            String agentName) {

        try {
            if (taskId == null || taskId.isBlank()) {
                return buildError("参数错误: taskId（任务 ID）不能为空");
            }
            if (agentName == null || agentName.isBlank()) {
                return buildError("参数错误: agentName（Agent 名称）不能为空");
            }

            Task task = taskService.completeTask(taskId, agentName);

            log.info("[CompleteTaskTool] 任务已完成: id={}, agent={}", taskId, agentName);

            return COMPLETE_TASK_TEMPLATE.formatted(
                    task.getId(),
                    task.getSubject(),
                    task.getStatus().getValue(),
                    task.getOwner()
            );
        } catch (TaskNotFoundException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            log.error("[CompleteTaskTool] 完成任务失败: {}", e.getMessage(), e);
            return buildError("完成任务失败: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        log.warn("[CompleteTaskTool] {}", message);
        return message;
    }
}
