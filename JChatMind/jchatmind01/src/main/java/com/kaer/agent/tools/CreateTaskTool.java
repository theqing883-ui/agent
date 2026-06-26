package com.kaer.agent.tools;

import com.kaer.model.entity.Task;
import com.kaer.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.kaer.agent.ConstantPrompt.TASK_RETURN_TEMPLATE;

/**
 * 创建任务工具 —— 将大项目拆解为小步骤并写入系统的任务库
 *
 * <p>该工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认加载</p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class CreateTaskTool implements Tool {

    private final TaskService taskService;

    @Override
    public String getName() {
        return "createTask";
    }

    @Override
    public String getDescription() {
        return "将大项目拆解为小步骤，并写入系统的任务库（持久化）。创建一个新任务，可指定其依赖的前置任务。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "createTask",
            description = "创建一个新任务并写入任务库。可以指定前置依赖任务（blockedBy），新任务将在所有前置任务完成后才能被认领。"
    )
    public String createTask(
            @org.springframework.ai.tool.annotation.ToolParam(description = "任务的主题或简短标题")
            String subject,
            @org.springframework.ai.tool.annotation.ToolParam(description = "任务的具体内容和详细描述")
            String description,
            @org.springframework.ai.tool.annotation.ToolParam(description = "阻塞当前任务的前置任务 ID 列表（可选）", required = false)
            List<String> blockedBy) {

        try {
            if (subject == null || subject.isBlank()) {
                return buildError("参数错误: subject（任务主题）不能为空");
            }
            if (description == null || description.isBlank()) {
                return buildError("参数错误: description（任务描述）不能为空");
            }

            Task task = taskService.createTask(subject, description, blockedBy);

            // 提取并简化前置任务逻辑
            String blockedByText = (task.getBlockedBy() == null || task.getBlockedBy().isEmpty())
                    ? "无"
                    : String.join(", ", task.getBlockedBy());

            log.info("[CreateTaskTool] 任务已创建: id={}, subject={}", task.getId(), task.getSubject());

            return TASK_RETURN_TEMPLATE.formatted(
                    task.getId(),
                    task.getSubject(),
                    task.getDescription(),
                    task.getStatus().getValue(),
                    blockedByText
            );
        } catch (Exception e) {
            log.error("[CreateTaskTool] 创建任务失败: {}", e.getMessage(), e);
            return buildError("创建任务失败: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        log.warn("[CreateTaskTool] {}", message);
        return message;
    }
}
