package com.kaer.exception;

/**
 * 任务不存在异常
 * <p>当尝试访问一个不存在的任务 ID 时抛出此异常</p>
 */
public class TaskNotFoundException extends BizException {

    public TaskNotFoundException(String taskId) {
        super("任务不存在: " + taskId);
    }
}
