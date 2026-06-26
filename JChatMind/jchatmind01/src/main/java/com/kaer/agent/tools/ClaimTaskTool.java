package com.kaer.agent.tools;

import com.kaer.exception.TaskAlreadyClaimedException;
import com.kaer.exception.TaskDependencyNotMetException;
import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.kaer.agent.ConstantPrompt.CLAIM_TASK_TEMPLATE;

/**
 * 认领任务工具 —— Agent 认领一个待处理的任务并开始执行
 *
 * <p>该工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认加载</p>
 *
 * <p>认领约束：
 * <ol>
 *   <li>任务的所有前置依赖必须已完成</li>
 *   <li>任务当前状态必须为 pending（未被其他 Agent 认领）</li>
 * </ol>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ClaimTaskTool implements Tool {

    private final TaskService taskService;

    @Override
    public String getName() {
        return "claimTask";
    }

    @Override
    public String getDescription() {
        return "认领一个待处理的任务。认领前会自动检查前置依赖是否全部完成，以及任务是否已被其他 Agent 认领。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "claimTask",
            description = "认领一个待处理的任务。会自动检查前置依赖是否全部完成，以及任务是否已被其他 Agent 认领。认领成功后任务状态变为 in_progress。"
    )
    public String claimTask(
            @org.springframework.ai.tool.annotation.ToolParam(description = "要认领的任务的唯一标识符 (ID)")
            String taskId,
            @org.springframework.ai.tool.annotation.ToolParam(description = "认领该任务的 Agent 名称")
            String agentName) {

        try {
            if (taskId == null || taskId.isBlank()) {
                return buildError("参数错误: taskId（任务 ID）不能为空");
            }
            if (agentName == null || agentName.isBlank()) {
                return buildError("参数错误: agentName（Agent 名称）不能为空");
            }

            Task task = taskService.claimTask(taskId, agentName);

            log.info("[ClaimTaskTool] 任务已被认领: id={}, agent={}", taskId, agentName);

            return CLAIM_TASK_TEMPLATE.formatted(
                    task.getId(),
                    task.getSubject(),
                    task.getStatus().getValue(),
                    task.getOwner()
            );
        } catch (TaskNotFoundException e) {
            return buildError(e.getMessage());
        } catch (TaskDependencyNotMetException e) {
            return buildError(e.getMessage() + "\n请等待前置任务全部完成后再尝试认领。");
        } catch (TaskAlreadyClaimedException e) {
            return buildError(e.getMessage() + "\n请选择其他 pending 状态的任务。");
        } catch (Exception e) {
            log.error("[ClaimTaskTool] 认领任务失败: {}", e.getMessage(), e);
            return buildError("认领任务失败: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        log.warn("[ClaimTaskTool] {}", message);
        return message;
    }
}
