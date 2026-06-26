package com.kaer.model.entity;

import com.kaer.model.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;


@Data
@Builder
public class Task {

    @ToolParam(description = "任务的唯一标识符 (ID)")
    private String id;

    @ToolParam(description = "任务的主题或简短标题")
    private String subject;

    @ToolParam(description = "任务的具体内容和详细描述")
    private String description;

    @ToolParam(description = "任务的当前状态。允许的值必须是：'pending'（待处理）, 'in_progress'（进行中）, 或 'completed'（已完成）")
    private TaskStatus status;

    @ToolParam(description = "任务的负责人或拥有者的名字")
    private String owner;

    @ToolParam(description = "阻塞当前任务的前置任务 ID 列表（即必须先完成哪些任务才能开始此任务）")
    private List<String> blockedBy;
}