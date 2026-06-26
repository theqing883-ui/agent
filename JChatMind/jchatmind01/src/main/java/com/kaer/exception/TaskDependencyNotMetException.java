package com.kaer.exception;

import java.util.List;

/**
 * 任务依赖未满足异常
 * <p>当 Agent 尝试认领一个其前置任务尚未全部完成的任务时抛出此异常</p>
 */
public class TaskDependencyNotMetException extends BizException {

    public TaskDependencyNotMetException(String taskId, List<String> blockedBy) {
        super("任务 " + taskId + " 的前置任务尚未完成: " + String.join(", ", blockedBy));
    }
}
