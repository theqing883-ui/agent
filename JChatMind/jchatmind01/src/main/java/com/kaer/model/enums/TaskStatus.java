package com.kaer.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 * <p>定义任务在其生命周期中的三种状态：
 * PENDING（待处理）、IN_PROGRESS（进行中）、COMPLETED（已完成）</p>
 *
 * <p>使用 @JsonValue 注解确保 Jackson 序列化时输出小写字符串值，
 * 以便 Spring AI 工具调用时 LLM 能看到可读的状态标识</p>
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    /**
     * 待处理：任务已创建但尚未被任何 Agent 认领
     */
    PENDING("pending"),

    /**
     * 进行中：任务已被某个 Agent 认领，正在执行中
     */
    IN_PROGRESS("in_progress"),

    /**
     * 已完成：任务已成功完成
     */
    COMPLETED("completed");

    /**
     * 状态的小写字符串值，用于 Jackson 序列化和 LLM 交互
     */
    @JsonValue
    private final String value;

    /**
     * 根据字符串值获取对应的枚举常量
     *
     * @param value 小写状态值（如 "pending", "in_progress", "completed"）
     * @return 对应的 TaskStatus 枚举常量
     * @throws IllegalArgumentException 当传入未知的状态值时抛出
     */
    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + value);
    }
}
