package com.kaer.service;

import com.kaer.exception.BizException;
import com.kaer.exception.TaskAlreadyClaimedException;
import com.kaer.exception.TaskDependencyNotMetException;
import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.model.enums.TaskStatus;
import com.kaer.model.vo.TaskSummary;
import com.kaer.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskService 单元测试
 *
 * <p>测试 TaskServiceImpl 的所有核心业务逻辑，包括：
 * 任务创建、查询、依赖检查、认领、完成，以及各种异常和边界情况</p>
 *
 * <p>使用纯 JUnit 5，无需 Spring 容器 —— TaskServiceImpl 基于 ConcurrentHashMap 自包含</p>
 */
class TaskServiceTest {

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl();
    }

    // ==================== 创建任务测试 ====================

    @Test
    void testCreateTask() {
        Task task = taskService.createTask("实现登录功能", "用户可以通过用户名和密码登录系统", null);

        assertNotNull(task.getId());
        assertTrue(task.getId().startsWith("TASK-"));
        assertEquals("实现登录功能", task.getSubject());
        assertEquals("用户可以通过用户名和密码登录系统", task.getDescription());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertNull(task.getOwner());
        assertTrue(task.getBlockedBy().isEmpty());
    }

    @Test
    void testCreateTaskWithBlockedBy() {
        Task taskA = taskService.createTask("任务A", "描述A", null);
        Task taskB = taskService.createTask("任务B", "描述B", List.of(taskA.getId()));

        assertEquals(1, taskB.getBlockedBy().size());
        assertTrue(taskB.getBlockedBy().contains(taskA.getId()));
    }

    // ==================== 查询任务测试 ====================

    @Test
    void testGetTaskNotFound() {
        assertThrows(TaskNotFoundException.class, () -> {
            taskService.getTask("TASK-nonexistent");
        });
    }

    @Test
    void testGetTaskSuccess() {
        Task created = taskService.createTask("测试任务", "测试描述", null);
        Task found = taskService.getTask(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals(created.getSubject(), found.getSubject());
        assertEquals(created.getDescription(), found.getDescription());
    }

    @Test
    void testListTasks() {
        // 初始为空
        assertTrue(taskService.listTasks().isEmpty());

        // 创建 3 个任务
        taskService.createTask("任务1", "描述1", null);
        taskService.createTask("任务2", "描述2", null);
        taskService.createTask("任务3", "描述3", null);

        List<TaskSummary> tasks = taskService.listTasks();
        assertEquals(3, tasks.size());

        // 验证摘要不包含 description（TaskSummary 类没有 description 字段）
        for (TaskSummary summary : tasks) {
            assertNotNull(summary.getId());
            assertNotNull(summary.getSubject());
            assertNotNull(summary.getStatus());
        }
    }

    // ==================== 依赖检查测试 ====================

    @Test
    void testCanStartWithoutDependencies() {
        Task task = taskService.createTask("无依赖任务", "描述", null);
        assertTrue(taskService.canStart(task.getId()));
    }

    @Test
    void testCanStartWithIncompleteDependency() {
        Task taskA = taskService.createTask("前置任务", "描述A", null);
        Task taskB = taskService.createTask("后置任务", "描述B", List.of(taskA.getId()));

        // A 未完成，B 不能开始
        assertFalse(taskService.canStart(taskB.getId()));
    }

    @Test
    void testCanStartAfterDependencyCompleted() {
        Task taskA = taskService.createTask("前置任务", "描述A", null);
        Task taskB = taskService.createTask("后置任务", "描述B", List.of(taskA.getId()));

        // 认领并完成 A
        taskService.claimTask(taskA.getId(), "agent1");
        taskService.completeTask(taskA.getId(), "agent1");

        // B 现在可以开始了
        assertTrue(taskService.canStart(taskB.getId()));
    }

    // ==================== 认领任务测试 ====================

    @Test
    void testClaimTaskSuccess() {
        Task task = taskService.createTask("认领测试", "描述", null);
        Task claimed = taskService.claimTask(task.getId(), "agent1");

        assertEquals(TaskStatus.IN_PROGRESS, claimed.getStatus());
        assertEquals("agent1", claimed.getOwner());
    }

    @Test
    void testClaimTaskAlreadyClaimed() {
        Task task = taskService.createTask("认领冲突测试", "描述", null);
        taskService.claimTask(task.getId(), "agent1");

        // 第二个 Agent 尝试认领同一任务
        assertThrows(TaskAlreadyClaimedException.class, () -> {
            taskService.claimTask(task.getId(), "agent2");
        });
    }

    @Test
    void testClaimTaskWithUnmetDependency() {
        Task taskA = taskService.createTask("前置任务", "描述A", null);
        Task taskB = taskService.createTask("依赖任务", "描述B", List.of(taskA.getId()));

        // A 未完成时尝试认领 B
        assertThrows(TaskDependencyNotMetException.class, () -> {
            taskService.claimTask(taskB.getId(), "agent1");
        });
    }

    @Test
    void testClaimTaskWithMetDependency() {
        Task taskA = taskService.createTask("前置任务", "描述A", null);
        Task taskB = taskService.createTask("依赖任务", "描述B", List.of(taskA.getId()));

        // 先认领并完成 A
        taskService.claimTask(taskA.getId(), "agent1");
        taskService.completeTask(taskA.getId(), "agent1");

        // 现在可以认领 B 了
        Task claimedB = taskService.claimTask(taskB.getId(), "agent2");
        assertEquals(TaskStatus.IN_PROGRESS, claimedB.getStatus());
        assertEquals("agent2", claimedB.getOwner());
    }

    @Test
    void testClaimCompletedTask() {
        Task task = taskService.createTask("已完成任务", "描述", null);
        taskService.claimTask(task.getId(), "agent1");
        taskService.completeTask(task.getId(), "agent1");

        // 尝试认领已完成的任务
        assertThrows(BizException.class, () -> {
            taskService.claimTask(task.getId(), "agent2");
        });
    }

    // ==================== 完成任务测试 ====================

    @Test
    void testCompleteTaskSuccess() {
        Task task = taskService.createTask("完成测试", "描述", null);
        taskService.claimTask(task.getId(), "agent1");
        Task completed = taskService.completeTask(task.getId(), "agent1");

        assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        assertEquals("agent1", completed.getOwner());
    }

    @Test
    void testCompleteTaskWrongOwner() {
        Task task = taskService.createTask("完成权限测试", "描述", null);
        taskService.claimTask(task.getId(), "agent1");

        // agent2 尝试完成 agent1 认领的任务
        assertThrows(BizException.class, () -> {
            taskService.completeTask(task.getId(), "agent2");
        });
    }

    @Test
    void testCompleteTaskNotInProgress() {
        Task task = taskService.createTask("完成状态测试", "描述", null);

        // 未认领就直接完成
        assertThrows(BizException.class, () -> {
            taskService.completeTask(task.getId(), "agent1");
        });
    }

    @Test
    void testCompleteTaskNotFound() {
        assertThrows(TaskNotFoundException.class, () -> {
            taskService.completeTask("TASK-nonexistent", "agent1");
        });
    }

    // ==================== 模拟多 Agent 工作流测试 ====================

    @Test
    void testMultiAgentWorkflow() {
        // 场景：模拟两个 Agent 协作完成任务拆解与委派

        // Agent-Parent 创建任务拆解
        Task taskDesign = taskService.createTask("系统设计", "完成系统架构设计文档", null);
        Task taskBackend = taskService.createTask("后端开发", "实现后端 API 接口", List.of(taskDesign.getId()));
        Task taskFrontend = taskService.createTask("前端开发", "实现前端页面", List.of(taskDesign.getId()));
        Task taskIntegration = taskService.createTask("集成测试", "前后端联调测试", List.of(taskBackend.getId(), taskFrontend.getId()));

        // 验证初始状态：只有"系统设计"可以开始
        assertTrue(taskService.canStart(taskDesign.getId()));
        assertFalse(taskService.canStart(taskBackend.getId()));
        assertFalse(taskService.canStart(taskFrontend.getId()));
        assertFalse(taskService.canStart(taskIntegration.getId()));

        // Agent-Architect 领取并完成系统设计
        Task design = taskService.claimTask(taskDesign.getId(), "Agent-Architect");
        assertEquals("Agent-Architect", design.getOwner());
        taskService.completeTask(taskDesign.getId(), "Agent-Architect");

        // 系统设计完成后，后端和前端的依赖解除
        assertTrue(taskService.canStart(taskBackend.getId()));
        assertTrue(taskService.canStart(taskFrontend.getId()));
        assertFalse(taskService.canStart(taskIntegration.getId())); // 仍被阻塞

        // Agent-Backend 领取后端开发
        taskService.claimTask(taskBackend.getId(), "Agent-Backend");
        taskService.completeTask(taskBackend.getId(), "Agent-Backend");

        // 仅完成后端，集成测试仍被前端阻塞
        assertFalse(taskService.canStart(taskIntegration.getId()));

        // Agent-Frontend 领取前端开发
        taskService.claimTask(taskFrontend.getId(), "Agent-Frontend");
        taskService.completeTask(taskFrontend.getId(), "Agent-Frontend");

        // 前后端都完成，集成测试可以开始
        assertTrue(taskService.canStart(taskIntegration.getId()));

        // Agent-QA 领取并完成集成测试
        taskService.claimTask(taskIntegration.getId(), "Agent-QA");
        taskService.completeTask(taskIntegration.getId(), "Agent-QA");

        // 验证所有任务都已完成
        List<TaskSummary> tasks = taskService.listTasks();
        assertEquals(4, tasks.size());
        for (TaskSummary t : tasks) {
            assertEquals(TaskStatus.COMPLETED, t.getStatus());
        }
    }
}
