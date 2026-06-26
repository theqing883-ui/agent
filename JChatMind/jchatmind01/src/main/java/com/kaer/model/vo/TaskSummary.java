package com.kaer.model.vo;

import com.kaer.model.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 任务摘要视图对象
 * <p>用于 listTasks 接口返回，故意省略 description 字段以节省 LLM Token 消耗</p>
 */
@Data
@Builder
public class TaskSummary {

    /**
     * 任务唯一标识
     */
    private String id;

    /**
     * 任务主题/简短标题
     */
    private String subject;

    /**
     * 任务当前状态
     */
    private TaskStatus status;

    /**
     * 任务负责人（可能为 null）
     */
    private String owner;

    /**
     * 阻塞当前任务的前置任务 ID 列表
     */
    private List<String> blockedBy;
}
