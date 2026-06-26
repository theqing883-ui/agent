package com.kaer.service;

import com.kaer.exception.BizException;
import com.kaer.exception.TaskAlreadyClaimedException;
import com.kaer.exception.TaskDependencyNotMetException;
import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.model.vo.TaskSummary;

import java.util.List;

/**
 * 任务管理服务接口
 * <p>提供任务的全生命周期管理：创建、查询、依赖检查、认领、完成</p>
 *
 * <p>线程安全：所有实现必须保证在多 Agent 并发访问下的数据一致性</p>
 */
public interface TaskService {

    /**
     * 创建新任务
     *
     * @param subject     任务主题/简短标题
     * @param description 任务详细描述
     * @param blockedBy   前置依赖任务 ID 列表（可为 null 或空列表）
     * @return 创建好的 Task 对象（状态为 PENDING，owner 为空，ID 自动生成）
     */
    Task createTask(String subject, String description, List<String> blockedBy);

    /**
     * 获取所有任务的摘要列表
     * <p>返回的摘要对象故意省略 description 字段，以节省 LLM Token 消耗</p>
     *
     * @return 任务摘要列表（可能为空列表）
     */
    List<TaskSummary> listTasks();

    /**
     * 根据任务 ID 获取任务完整信息
     *
     * @param taskId 任务唯一标识
     * @return 完整的 Task 对象（包含 description）
     * @throws TaskNotFoundException 当任务不存在时抛出
     */
    Task getTask(String taskId) throws TaskNotFoundException;

    /**
     * 依赖检查：判断任务的所有前置任务是否都已完成
     * <p>如果 blockedBy 为空，直接返回 true</p>
     *
     * @param taskId 要检查的任务 ID
     * @return true = 所有前置任务已完成或没有前置任务；false = 存在未完成的前置任务
     */
    boolean canStart(String taskId);

    /**
     * Agent 认领任务
     * <p>原子操作：确保在高并发下不会出现重复认领</p>
     *
     * <p>约束条件（任一不满足则抛出异常）：
     * <ol>
     *   <li>任务必须存在</li>
     *   <li>所有前置任务必须已完成（内部调用 canStart）</li>
     *   <li>任务当前状态必须为 PENDING（未被他人认领）</li>
     * </ol>
     *
     * @param taskId    要认领的任务 ID
     * @param agentName 认领的 Agent 名称
     * @return 更新后的 Task 对象（状态为 IN_PROGRESS）
     * @throws TaskNotFoundException         任务不存在
     * @throws TaskDependencyNotMetException 前置任务未完成
     * @throws TaskAlreadyClaimedException   任务已被其他 Agent 认领
     */
    Task claimTask(String taskId, String agentName)
            throws TaskNotFoundException, TaskDependencyNotMetException, TaskAlreadyClaimedException;

    /**
     * 完成任务
     * <p>原子操作：确保状态变更的一致性</p>
     *
     * <p>约束条件：
     * <ol>
     *   <li>任务必须存在</li>
     *   <li>当前状态必须为 IN_PROGRESS</li>
     *   <li>调用者必须与任务的 owner 匹配</li>
     * </ol>
     *
     * @param taskId    要完成的任务 ID
     * @param agentName 完成任务的 Agent 名称（必须与认领者一致）
     * @return 更新后的 Task 对象（状态为 COMPLETED）
     * @throws TaskNotFoundException 任务不存在
     * //@throws BizException          状态不正确或 owner 不匹配时抛出
     */
    Task completeTask(String taskId, String agentName) throws TaskNotFoundException;
}
