package com.kaer.exception;

/**
 * 任务已被认领异常
 * <p>当 Agent 尝试认领一个已被其他 Agent 认领的任务时抛出此异常</p>
 */
public class TaskAlreadyClaimedException extends BizException {

    public TaskAlreadyClaimedException(String taskId, String currentOwner) {
        super("任务 " + taskId + " 已被 " + currentOwner + " 认领");
    }
}
