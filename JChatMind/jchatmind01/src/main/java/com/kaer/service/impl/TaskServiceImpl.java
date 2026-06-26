package com.kaer.service.impl;

import com.kaer.exception.BizException;
import com.kaer.exception.TaskAlreadyClaimedException;
import com.kaer.exception.TaskDependencyNotMetException;
import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.model.enums.TaskStatus;
import com.kaer.model.vo.TaskSummary;
import com.kaer.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务管理服务实现
 *
 * <p>使用 ConcurrentHashMap 作为内存级任务库，通过 compute() 方法保证
 * 认领（claim）和完成（complete）操作的原子性，防止多 Agent 并发冲突</p>
 *
 * <p>线程安全保证：
 * <ul>
 *   <li>ConcurrentHashMap 提供分段锁，保证单 key 的原子操作</li>
 *   <li>claimTask 和 completeTask 使用 compute() 实现 check-and-set</li>
 *   <li>canStart() 仅读取其他 key 的状态，不修改 map，在 compute() 回调中调用安全</li>
 * </ul>
 *
 * <p>预留持久化接口：当前为纯内存实现，后续可替换为数据库-backed 实现</p>
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    /**
     * 内存任务库，key 为任务 ID，value 为任务实体
     */
    private final Map<String, Task> taskStore = new ConcurrentHashMap<>();

    /**
     * 创建新任务
     * <p>生成格式为 "TASK-{UUID}" 的唯一 ID，状态默认 PENDING，owner 为空</p>
     */
    @Override
    public Task createTask(String subject, String description, List<String> blockedBy) {
        String taskId = "TASK-" + UUID.randomUUID();
        List<String> normalizedBlockedBy = (blockedBy != null) ? new ArrayList<>(blockedBy) : List.of();

        Task task = Task.builder()
                .id(taskId)
                .subject(subject)
                .description(description)
                .status(TaskStatus.PENDING)
                .owner(null)
                .blockedBy(normalizedBlockedBy)
                .build();

        taskStore.put(taskId, task);
        log.info("[TaskService] 任务已创建: id={}, subject={}, blockedBy={}", taskId, subject, normalizedBlockedBy);
        return task;
    }

    /**
     * 获取所有任务的摘要列表（不含 description）
     */
    @Override
    public List<TaskSummary> listTasks() {
        return taskStore.values().stream()
                .map(task -> TaskSummary.builder()
                        .id(task.getId())
                        .subject(task.getSubject())
                        .status(task.getStatus())
                        .owner(task.getOwner())
                        .blockedBy(task.getBlockedBy())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取任务完整信息
     *
     * @throws TaskNotFoundException 任务不存在
     */
    @Override
    public Task getTask(String taskId) throws TaskNotFoundException {
        Task task = taskStore.get(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    /**
     * 依赖检查：所有 blockedBy 中的任务必须全部为 COMPLETED
     * <p>如果 blockedBy 为空，直接返回 true</p>
     * <p>如果 blockedBy 中包含不存在的任务 ID，返回 false</p>
     */
    @Override
    public boolean canStart(String taskId) {
        Task task = taskStore.get(taskId);
        if (task == null) {
            return false;
        }
        List<String> blockedBy = task.getBlockedBy();
        if (blockedBy == null || blockedBy.isEmpty()) {
            return true;
        }
        for (String depId : blockedBy) {
            Task depTask = taskStore.get(depId);
            if (depTask == null || depTask.getStatus() != TaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原子认领任务
     *
     * <p>使用 ConcurrentHashMap.compute() 确保同一个任务不会被两个 Agent 同时认领。
     * 在 compute() 回调中检查所有约束条件，任一不满足则抛出异常，
     * compute() 的语义保证抛异常时 map 条目不会被修改</p>
     */
    @Override
    public Task claimTask(String taskId, String agentName)
            throws TaskNotFoundException, TaskDependencyNotMetException, TaskAlreadyClaimedException {

        /*compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction)，
        从Map 中取出 key对应的value作为后面函数的传入参数/
        * */
        return taskStore.compute(taskId, (id, task) -> {
            // 约束 1：任务必须存在
            if (task == null) {
                throw new TaskNotFoundException(taskId);
            }

            // 约束 2：任务状态必须为 PENDING（防止重复认领）
            if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                throw new TaskAlreadyClaimedException(taskId, task.getOwner());
            }
            if (task.getStatus() == TaskStatus.COMPLETED) {
                throw new BizException("任务 " + taskId + " 已完成，无法认领");
            }

            // 约束 3：所有前置任务必须已完成
            if (!canStart(taskId)) {
                throw new TaskDependencyNotMetException(taskId, task.getBlockedBy());
            }

            // 检查通过，执行认领
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setOwner(agentName);
            log.info("[TaskService] 任务已被认领: id={}, agent={}", taskId, agentName);
            return task;
        });
    }

    /**
     * 原子完成任务
     *
     * <p>使用 ConcurrentHashMap.compute() 确保状态变更的原子性。
     * 验证 owner 匹配和状态正确性后才变更为 COMPLETED</p>
     */
    @Override
    public Task completeTask(String taskId, String agentName) throws TaskNotFoundException {
        return taskStore.compute(taskId, (id, task) -> {
            // 约束 1：任务必须存在
            if (task == null) {
                throw new TaskNotFoundException(taskId);
            }

            // 约束 2：调用者必须是任务的 owner
            if (task.getOwner() == null || !task.getOwner().equals(agentName)) {
                throw new BizException("Agent " + agentName + " 无权完成任务 " + taskId
                        + "（当前 owner: " + task.getOwner() + "）");
            }

            // 约束 3：任务状态必须为 IN_PROGRESS
            if (task.getStatus() != TaskStatus.IN_PROGRESS) {
                throw new BizException("任务 " + taskId + " 当前状态为 " + task.getStatus().getValue()
                        + "，无法完成（需要 in_progress）");
            }

            // 检查通过，执行完成
            task.setStatus(TaskStatus.COMPLETED);
            log.info("[TaskService] 任务已完成: id={}, agent={}", taskId, agentName);
            return task;
        });
    }
}
