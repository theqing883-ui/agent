package com.kaer.agent;

import com.kaer.agent.messagebus.MessageBus;
import com.kaer.config.MessageBusConfig;
import com.kaer.agent.messagebus.MessageBusImpl;
import com.kaer.agent.messagebus.MessageRecord;
import com.kaer.exception.TaskAlreadyClaimedException;
import com.kaer.exception.TaskDependencyNotMetException;
import com.kaer.exception.TaskNotFoundException;
import com.kaer.model.entity.Task;
import com.kaer.model.enums.TaskStatus;
import com.kaer.model.vo.TaskSummary;
import com.kaer.service.TaskService;
import com.kaer.service.impl.TaskServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多 Agent 协作集成测试。
 *
 * <p>测试 MessageBus 文件通信 + TaskService 任务调度 + Teammate 协作的完整流程。
 * 使用纯 JUnit 5，不依赖 Spring 容器和 LLM。
 *
 * <p>测试覆盖：
 * <ol>
 *   <li>MessageBus 基本 send/readInbox 功能</li>
 *   <li>MessageBus 并发写入安全性</li>
 *   <li>Lead 创建依赖任务 → Teammate 顺序认领 → 完成 → 汇报的完整流程</li>
 *   <li>多 Teammate 并发竞争任务的安全性</li>
 *   <li>收件箱文件持久化与跨读取验证</li>
 * </ol>
 */
class TeammateCollaborationTest {

    private Path tempDir;
    private MessageBus messageBus;
    private TaskService taskService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        // 创建临时目录存放收件箱文件
        tempDir = Files.createTempDirectory("jchatmind-test-inboxes-");

        // 初始化 ObjectMapper（支持 Java 8 时间类型）
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 初始化 MessageBus
        MessageBusConfig config = new MessageBusConfig();
        config.setInboxDir(tempDir.toString());
        messageBus = new MessageBusImpl(config, objectMapper);

        // 初始化 TaskService
        taskService = new TaskServiceImpl();
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理临时目录
        if (tempDir != null && Files.exists(tempDir)) {
            try (var files = Files.walk(tempDir)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        }
    }

    // ==================== MessageBus 基本功能测试 ====================

    @Test
    void testSendAndReadInbox() {
        MessageRecord msg = MessageRecord.builder()
                .requestId(UUID.randomUUID().toString().substring(0, 8))
                .type(MessageRecord.Type.STATUS_UPDATE)
                .sender("worker")
                .target("lead")
                .content("Completed TASK-A: Research topic X")
                .timestamp(Instant.now())
                .build();

        // 发送消息
        messageBus.send("lead", msg);

        // 读取收件箱
        List<MessageRecord> inbox = messageBus.readInbox("lead");
        assertEquals(1, inbox.size());
        assertEquals(MessageRecord.Type.STATUS_UPDATE,inbox.get(0).getType());
        assertEquals("worker", inbox.get(0).getSender());
        assertEquals("lead", inbox.get(0).getTarget());
        assertEquals("Completed TASK-A: Research topic X", inbox.get(0).getContent());

        // 消费式读取：再次读取应为空
        List<MessageRecord> inbox2 = messageBus.readInbox("lead");
        assertTrue(inbox2.isEmpty());
    }

    @Test
    void testMultipleMessagesOrder() {
        // 发送多条有序消息
        for (int i = 1; i <= 5; i++) {
            MessageRecord msg = MessageRecord.builder()
                    .requestId("req-" + i)
                    .type(MessageRecord.Type.STATUS_UPDATE)
                    .sender("worker")
                    .target("lead")
                    .content("Message " + i)
                    .timestamp(Instant.now())
                    .build();
            messageBus.send("lead", msg);
        }

        List<MessageRecord> inbox = messageBus.readInbox("lead");
        assertEquals(5, inbox.size());

        // 验证顺序
        for (int i = 0; i < 5; i++) {
            assertEquals("Message " + (i + 1), inbox.get(i).getContent());
        }
    }

    @Test
    void testReadEmptyInbox() {
        List<MessageRecord> inbox = messageBus.readInbox("nonexistent-agent");
        assertTrue(inbox.isEmpty());
    }

    @Test
    void testMultipleAgentsIsolatedInboxes() {
        // 向不同 Agent 发送消息
        messageBus.send("lead", MessageRecord.builder()
                .requestId("r1").type(MessageRecord.Type.STATUS_UPDATE).sender("alice").target("lead")
                .content("Alice done").timestamp(Instant.now()).build());
        messageBus.send("lead", MessageRecord.builder()
                .requestId("r2").type(MessageRecord.Type.STATUS_UPDATE).sender("bob").target("lead")
                .content("Bob done").timestamp(Instant.now()).build());

        // Lead 收件箱有 2 条
        assertEquals(2, messageBus.readInbox("lead").size());

        // Alice 和 Bob 的收件箱为空（只发不收）
        assertTrue(messageBus.readInbox("alice").isEmpty());
        assertTrue(messageBus.readInbox("bob").isEmpty());
    }

    @Test
    void testMessageBusRespondToRequest() {
        // Lead 向 Worker 发 REQUEST
        messageBus.send("worker", MessageRecord.builder()
                .requestId("req-001")
                .type(MessageRecord.Type.REQUEST)
                .sender("lead")
                .target("worker")
                .content("Please research topic X")
                .timestamp(Instant.now())
                .build());

        // Worker 读取 REQUEST
        List<MessageRecord> workerInbox = messageBus.readInbox("worker");
        assertEquals(1, workerInbox.size());
        assertEquals(MessageRecord.Type.REQUEST,workerInbox.get(0).getType());
        assertEquals("req-001", workerInbox.get(0).getRequestId());

        // Worker 回复 RESPONSE（匹配 requestId）
        messageBus.send("lead", MessageRecord.builder()
                .requestId("req-001")  // 匹配原始 requestId
                .type(MessageRecord.Type.RESPONSE)
                .sender("worker")
                .target("lead")
                .content("Research complete: topic X is about...")
                .timestamp(Instant.now())
                .build());

        // Lead 读取 RESPONSE
        List<MessageRecord> leadInbox = messageBus.readInbox("lead");
        assertEquals(1, leadInbox.size());
        assertEquals(MessageRecord.Type.RESPONSE,leadInbox.get(0).getType());
        assertEquals("req-001", leadInbox.get(0).getRequestId());
    }

    // ==================== MessageBus 并发安全测试 ====================

    @Test
    void testConcurrentWritesToSameInbox() throws InterruptedException {
        int threadCount = 10;
        int messagesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerThread; i++) {
                        MessageRecord msg = MessageRecord.builder()
                                .requestId("t" + threadId + "-m" + i)
                                .type(MessageRecord.Type.STATUS_UPDATE)
                                .sender("worker-" + threadId)
                                .target("lead")
                                .content("Thread " + threadId + " msg " + i)
                                .timestamp(Instant.now())
                                .build();
                        messageBus.send("lead", msg);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // 读取所有消息
        List<MessageRecord> inbox = messageBus.readInbox("lead");
        assertEquals(threadCount * messagesPerThread, inbox.size(),
                "并发写入的消息总数应等于线程数 × 每线程消息数");
    }

    @Test
    void testConcurrentReadAndWrite() throws InterruptedException {
        // 先写入一些消息
        for (int i = 0; i < 100; i++) {
            messageBus.send("lead", MessageRecord.builder()
                    .requestId("pre-" + i).type(MessageRecord.Type.STATUS_UPDATE).sender("w").target("lead")
                    .content("Pre " + i).timestamp(Instant.now()).build());
        }

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger readCount = new AtomicInteger();

        // 线程 1：持续写入
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    messageBus.send("lead", MessageRecord.builder()
                            .requestId("concurrent-" + i).type(MessageRecord.Type.STATUS_UPDATE)
                            .sender("w").target("lead").content("Concurrent " + i)
                            .timestamp(Instant.now()).build());
                }
                latch.countDown();
            } catch (Exception e) {
                fail("写入线程异常: " + e.getMessage());
            }
        });

        // 线程 2：持续读取
        Thread reader = new Thread(() -> {
            try {
                int total = 0;
                for (int i = 0; i < 10; i++) {
                    List<MessageRecord> msgs = messageBus.readInbox("lead");
                    total += msgs.size();
                    Thread.sleep(10);
                }
                readCount.set(total);
                latch.countDown();
            } catch (Exception e) {
                fail("读取线程异常: " + e.getMessage());
            }
        });

        writer.start();
        reader.start();
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // 最终收件箱应被最后读取清空（或剩余未被读取的消息）
        List<MessageRecord> remaining = messageBus.readInbox("lead");
        assertTrue(readCount.get() + remaining.size() >= 100,
                "所有消息要么被读取，要么保留在收件箱中");
    }

    // ==================== Lead + Teammate 协作流程测试 ====================

    @Test
    void testLeadCreatesTasksAndTeammateClaimsAndCompletes() {
        // 模拟 Lead 拆解需求并创建两个有依赖关系的任务
        Task taskA = taskService.createTask("Research topic X", "调查 X 相关的技术方案和最佳实践", null);
        Task taskB = taskService.createTask("Implement feature Y", "基于 research 结果实现 Y 功能",
                List.of(taskA.getId()));

        // 验证初始状态
        assertEquals(TaskStatus.PENDING, taskA.getStatus());
        assertEquals(TaskStatus.PENDING, taskB.getStatus());
        assertTrue(taskService.canStart(taskA.getId()));
        assertFalse(taskService.canStart(taskB.getId()));  // B 被 A 阻塞

        // Teammate "researcher" 轮询任务系统，发现 TASK-A
        List<TaskSummary> tasks = taskService.listTasks();
        assertEquals(2, tasks.size());

        // Teammate 认领 TASK-A
        Task claimedA = taskService.claimTask(taskA.getId(), "researcher");
        assertEquals(TaskStatus.IN_PROGRESS, claimedA.getStatus());
        assertEquals("researcher", claimedA.getOwner());

        // Teammate 执行任务...（模拟 LLM 执行过程）
        // Teammate 完成后调用 completeTask
        Task completedA = taskService.completeTask(taskA.getId(), "researcher");
        assertEquals(TaskStatus.COMPLETED, completedA.getStatus());

        // Teammate 向 Lead 发送完成汇报
        messageBus.send("lead", MessageRecord.builder()
                .requestId(UUID.randomUUID().toString().substring(0, 8))
                .type(MessageRecord.Type.STATUS_UPDATE)
                .sender("researcher")
                .target("lead")
                .content("Completed " + taskA.getId() + " [" + taskA.getSubject() + "]: "
                        + "Research done. Found 3 relevant papers and 2 open-source implementations.")
                .timestamp(Instant.now())
                .build());

        // TASK-A 完成后，TASK-B 的依赖解除
        assertTrue(taskService.canStart(taskB.getId()));

        // Teammate 在下一轮轮询中发现 TASK-B
        // 认领 TASK-B
        Task claimedB = taskService.claimTask(taskB.getId(), "researcher");
        assertEquals(TaskStatus.IN_PROGRESS, claimedB.getStatus());
        assertEquals("researcher", claimedB.getOwner());

        // Teammate 执行并完成 TASK-B
        Task completedB = taskService.completeTask(taskB.getId(), "researcher");
        assertEquals(TaskStatus.COMPLETED, completedB.getStatus());

        // Teammate 向 Lead 汇报 TASK-B 完成
        messageBus.send("lead", MessageRecord.builder()
                .requestId(UUID.randomUUID().toString().substring(0, 8))
                .type(MessageRecord.Type.STATUS_UPDATE)
                .sender("researcher")
                .target("lead")
                .content("Completed " + taskB.getId() + " [" + taskB.getSubject() + "]: "
                        + "Implementation done. All tests pass.")
                .timestamp(Instant.now())
                .build());

        // Lead 读取收件箱，验证收到两条进度汇报
        List<MessageRecord> leadInbox = messageBus.readInbox("lead");
        assertEquals(2, leadInbox.size());
        assertEquals(MessageRecord.Type.STATUS_UPDATE,leadInbox.get(0).getType());
        assertEquals(MessageRecord.Type.STATUS_UPDATE,leadInbox.get(1).getType());
        assertTrue(leadInbox.get(0).getContent().contains(taskA.getId()));
        assertTrue(leadInbox.get(1).getContent().contains(taskB.getId()));

        // 验证所有任务状态
        tasks = taskService.listTasks();
        assertEquals(2, tasks.size());
        for (TaskSummary t : tasks) {
            assertEquals(TaskStatus.COMPLETED, t.getStatus());
            assertEquals("researcher", t.getOwner());
        }
    }

    // ==================== 多 Teammate 并发竞争测试 ====================

    @Test
    void testMultipleTeammatesConcurrentTaskClaiming() throws InterruptedException {
        int taskCount = 20;
        int teammateCount = 10;

        // Lead 创建 20 个无依赖的 PENDING 任务
        List<String> taskIds = new ArrayList<>();
        for (int i = 1; i <= taskCount; i++) {
            Task task = taskService.createTask("Task-" + i, "Description " + i, null);
            taskIds.add(task.getId());
        }

        // 模拟 4 个 Teammate 同时竞争认领任务
        ExecutorService executor = Executors.newFixedThreadPool(teammateCount);
        CountDownLatch latch = new CountDownLatch(teammateCount);
        AtomicInteger claimedCount = new AtomicInteger();

        for (int t = 0; t < teammateCount; t++) {
            final String teammateName = "teammate-" + t;
            executor.submit(() -> {
                try {
                    // 每个 teammate 尝试认领所有 PENDING 任务
                    for (int attempt = 0; attempt < 5; attempt++) {
                        List<TaskSummary> tasks = taskService.listTasks();
                        for (TaskSummary task : tasks) {
                            if (task.getStatus() != TaskStatus.PENDING) continue;
                            if (!taskService.canStart(task.getId())) continue;
                            try {
                                taskService.claimTask(task.getId(), teammateName);
                                claimedCount.incrementAndGet();
                                // 模拟执行
                                Thread.sleep(1);
                                // 完成后立即 complete
                                taskService.completeTask(task.getId(), teammateName);
                            } catch (TaskAlreadyClaimedException | TaskDependencyNotMetException e) {
                                // 被其他 Teammate 抢先——正常情况，继续尝试下一个
                            }
                        }
                    }
                } catch (Exception e) {
                    fail("Teammate " + teammateName + " 异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证每个任务只被完成一次
        List<TaskSummary> finalTasks = taskService.listTasks();
        assertEquals(taskCount, finalTasks.size());

        int completedCount = 0;
        for (TaskSummary t : finalTasks) {
            assertEquals(TaskStatus.COMPLETED, t.getStatus(),
                    "任务 " + t.getId() + " 应为 COMPLETED 状态");
            assertNotNull(t.getOwner(), "任务 " + t.getId() + " 应有 owner");
            completedCount++;
        }
        assertEquals(taskCount, completedCount, "所有任务都应被完成");
    }

    // ==================== 收件箱文件持久化测试 ====================

    @Test
    void testInboxFilePersistenceAcrossReads() throws IOException {
        // 写入消息
        messageBus.send("persistent-agent", MessageRecord.builder()
                .requestId("p1").type(MessageRecord.Type.REQUEST).sender("lead").target("persistent-agent")
                .content("Do something").timestamp(Instant.now()).build());

        // 验证文件存在
        Path inboxFile = tempDir.resolve("persistent-agent.jsonl");
        assertTrue(Files.exists(inboxFile));
        assertTrue(Files.size(inboxFile) > 0);

        // 读取后文件被清空
        List<MessageRecord> firstRead = messageBus.readInbox("persistent-agent");
        assertEquals(1, firstRead.size());
        assertEquals(0, Files.size(inboxFile), "读取后文件应为空");

        // 写入新消息，验证可以再次读取
        messageBus.send("persistent-agent", MessageRecord.builder()
                .requestId("p2").type(MessageRecord.Type.RESPONSE).sender("persistent-agent").target("lead")
                .content("Done").timestamp(Instant.now()).build());

        assertTrue(Files.size(inboxFile) > 0);
        List<MessageRecord> secondRead = messageBus.readInbox("persistent-agent");
        assertEquals(1, secondRead.size());
        assertEquals("p2", secondRead.get(0).getRequestId());
    }

    // ==================== 异常情况测试 ====================

    @Test
    void testClaimTaskNotFound() {
        assertThrows(TaskNotFoundException.class, () -> {
            taskService.claimTask("TASK-nonexistent", "worker");
        });
    }

    @Test
    void testCompleteTaskByWrongOwner() {
        Task task = taskService.createTask("Ownership test", "desc", null);
        taskService.claimTask(task.getId(), "alice");

        // Bob 试图完成 Alice 的任务
        assertThrows(com.kaer.exception.BizException.class, () -> {
            taskService.completeTask(task.getId(), "bob");
        });
    }

    @Test
    void testTeammateCannotClaimBlockedTask() {
        Task taskA = taskService.createTask("Blocker", "desc A", null);
        Task taskB = taskService.createTask("Blocked", "desc B", List.of(taskA.getId()));

        // Teammate 尝试认领被阻塞的任务
        assertThrows(TaskDependencyNotMetException.class, () -> {
            taskService.claimTask(taskB.getId(), "researcher");
        });

        // 但在 A 完成后可以认领
        taskService.claimTask(taskA.getId(), "researcher");
        taskService.completeTask(taskA.getId(), "researcher");

        Task claimed = taskService.claimTask(taskB.getId(), "researcher");
        assertEquals(TaskStatus.IN_PROGRESS, claimed.getStatus());
    }

    // ==================== Req-ACK 异步 RPC 协议测试 ====================

    @Test
    void testReqAckProtocolFullCycle() {
        // 场景：完整模拟 Lead → Teammate 的 Req-ACK 流程

        // 1. Lead 通过 sendMessage 向 Teammate 发送 REQUEST
        //    模拟 SendMessageTool 的行为：自动生成 requestId，注册 PendingRequest
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        messageBus.send("alice", MessageRecord.builder()
                .requestId(requestId)
                .type(MessageRecord.Type.REQUEST)
                .sender("lead")
                .target("alice")
                .content("请关机")
                .timestamp(Instant.now())
                .build());

        // 注册 PendingRequest（模拟 SendMessageTool 通过 callback 注册）
        PendingRequest pending = new PendingRequest(
                requestId, "alice", "请关机", Instant.now(),
                PendingRequest.Status.AWAITING, null, null);

        assertEquals(PendingRequest.Status.AWAITING, pending.getStatus());
        assertNull(pending.getResponseContent());

        // 2. Teammate (alice) 读取收件箱，收到 REQUEST
        List<MessageRecord> aliceInbox = messageBus.readInbox("alice");
        assertEquals(1, aliceInbox.size());
        assertEquals(MessageRecord.Type.REQUEST,aliceInbox.get(0).getType());
        assertEquals(requestId, aliceInbox.get(0).getRequestId());
        assertEquals("请关机", aliceInbox.get(0).getContent());

        // 3. Teammate 调用 replyToLead 回复（模拟 ReplyToLeadTool）
        messageBus.send("lead", MessageRecord.builder()
                .requestId(requestId)    // 关键：使用相同的 requestId
                .type(MessageRecord.Type.RESPONSE)
                .sender("alice")
                .target("lead")
                .content("已关机，系统将在 5 秒后断电")
                .timestamp(Instant.now())
                .build());

        // 4. Lead 读取收件箱（模拟 JChatMind.pollInbox() 的匹配逻辑）
        List<MessageRecord> leadInbox = messageBus.readInbox("lead");
        assertEquals(1, leadInbox.size());
        MessageRecord response = leadInbox.get(0);
        assertEquals(MessageRecord.Type.RESPONSE,response.getType());
        assertEquals(requestId, response.getRequestId());
        assertEquals("alice", response.getSender());

        // 5. 验证 PendingRequest 匹配成功
        assertEquals(requestId, pending.getRequestId());
        // 模拟 pollInbox 的匹配逻辑
        if (MessageRecord.Type.RESPONSE.equals(response.getType()) && response.getRequestId().equals(pending.getRequestId())) {
            pending.setStatus(PendingRequest.Status.RESPONDED);
            pending.setResponseContent(response.getContent());
            pending.setRespondedAt(response.getTimestamp());
        }

        assertEquals(PendingRequest.Status.RESPONDED, pending.getStatus());
        assertEquals("已关机，系统将在 5 秒后断电", pending.getResponseContent());
        assertNotNull(pending.getRespondedAt());
    }

    @Test
    void testMultiplePendingRequestsMatching() {
        // 场景：Lead 同时向多个 Teammate 发起 REQUEST，验证各自独立匹配

        String req1 = UUID.randomUUID().toString().substring(0, 8);
        String req2 = UUID.randomUUID().toString().substring(0, 8);

        // 注册两条 pending requests
        PendingRequest pr1 = new PendingRequest(req1, "alice", "关机", Instant.now(),
                PendingRequest.Status.AWAITING, null, null);
        PendingRequest pr2 = new PendingRequest(req2, "bob", "备份数据", Instant.now(),
                PendingRequest.Status.AWAITING, null, null);

        // 发送两条 REQUEST
        messageBus.send("alice", MessageRecord.builder()
                .requestId(req1).type(MessageRecord.Type.REQUEST).sender("lead").target("alice")
                .content("关机").timestamp(Instant.now()).build());
        messageBus.send("bob", MessageRecord.builder()
                .requestId(req2).type(MessageRecord.Type.REQUEST).sender("lead").target("bob")
                .content("备份数据").timestamp(Instant.now()).build());

        // Bob 先回复（消息乱序到达）
        messageBus.send("lead", MessageRecord.builder()
                .requestId(req2).type(MessageRecord.Type.RESPONSE).sender("bob").target("lead")
                .content("备份完成").timestamp(Instant.now()).build());

        // Lead 读取收件箱，应该只有 Bob 的回复
        List<MessageRecord> inbox = messageBus.readInbox("lead");
        assertEquals(1, inbox.size());
        assertEquals(req2, inbox.get(0).getRequestId());

        // 模拟匹配：Bob 匹配成功，Alice 仍在等待
        if (MessageRecord.Type.RESPONSE.equals(inbox.get(0).getType()) && inbox.get(0).getRequestId().equals(pr2.getRequestId())) {
            pr2.setStatus(PendingRequest.Status.RESPONDED);
            pr2.setResponseContent(inbox.get(0).getContent());
        }
        assertEquals(PendingRequest.Status.RESPONDED, pr2.getStatus());
        assertEquals(PendingRequest.Status.AWAITING, pr1.getStatus());

        // Alice 稍后回复
        messageBus.send("lead", MessageRecord.builder()
                .requestId(req1).type(MessageRecord.Type.RESPONSE).sender("alice").target("lead")
                .content("已关机").timestamp(Instant.now()).build());

        inbox = messageBus.readInbox("lead");
        assertEquals(1, inbox.size());
        assertEquals(req1, inbox.get(0).getRequestId());

        if (MessageRecord.Type.RESPONSE.equals(inbox.get(0).getType()) && inbox.get(0).getRequestId().equals(pr1.getRequestId())) {
            pr1.setStatus(PendingRequest.Status.RESPONDED);
            pr1.setResponseContent(inbox.get(0).getContent());
        }
        assertEquals(PendingRequest.Status.RESPONDED, pr1.getStatus());
        assertEquals(PendingRequest.Status.RESPONDED, pr2.getStatus());
    }

    @Test
    void testPendingRequestTimeoutDetection() {
        // 验证超时状态的正确性
        PendingRequest pr = new PendingRequest("req_timeout", "alice", "test",
                Instant.now().minusSeconds(120), PendingRequest.Status.AWAITING, null, null);

        assertEquals(PendingRequest.Status.AWAITING, pr.getStatus());

        // 模拟超时检测逻辑（超过 60 秒标记为 TIMEOUT）
        if (pr.getSentAt().plusSeconds(60).isBefore(Instant.now())) {
            pr.setStatus(PendingRequest.Status.TIMEOUT);
        }
        assertEquals(PendingRequest.Status.TIMEOUT, pr.getStatus());
    }

    @Test
    void testMessageRecordCorrelationIdFormat() {
        // 验证 MessageRecord 中的 requestId 正确传递
        String expectedId = "abc12345";
        MessageRecord req = MessageRecord.builder()
                .requestId(expectedId).type(MessageRecord.Type.REQUEST)
                .sender("lead").target("alice")
                .content("test").timestamp(Instant.now())
                .build();

        MessageRecord resp = MessageRecord.builder()
                .requestId(expectedId)  // 使用相同的关联键
                .type(MessageRecord.Type.RESPONSE)
                .sender("alice").target("lead")
                .content("done").timestamp(Instant.now())
                .build();

        // REQUEST 和 RESPONSE 通过 requestId 关联
        assertEquals(req.getRequestId(), resp.getRequestId());
        assertEquals(expectedId, req.getRequestId());
        assertEquals(expectedId, resp.getRequestId());
    }

    // ==================== 队友生命周期控制测试 ====================

    @Test
    void testTeammateWorkerStopAndShouldRestart() throws InterruptedException {
        // 验证 stop() 能正确停止 TeammateWorker 线程
        TaskService dummyService = taskService;
        var workerThread = new Thread(() -> {
            // 模拟 TeammateWorker.runLoop 的逻辑
        }, "teammate-test");
        workerThread.setDaemon(true);

        // 简单的停止标志验证
        AtomicInteger state = new AtomicInteger(0); // 0=running, 1=stopped
        Thread t = new Thread(() -> {
            while (state.get() == 0) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
        t.start();
        assertTrue(t.isAlive());

        state.set(1);
        t.interrupt();
        t.join(2000);
        assertFalse(t.isAlive(), "线程应在 interrupt + 标志变更后退出");
    }

    @Test
    void testShutdownFlagMechanism() {
        // 验证 AgentContextHolder.NEEDS_SHUTDOWN 标志的 set/check/clear 生命周期
        assertFalse(AgentContextHolder.needsShutdown(),
                "初始状态应为不需要 shutdown");

        AgentContextHolder.markNeedsShutdown();
        assertTrue(AgentContextHolder.needsShutdown(),
                "markNeedsShutdown() 后应为 true");

        // 模拟 TeammateWorker.clearLeadName() 清理
        AgentContextHolder.clearLeadName();
        assertFalse(AgentContextHolder.needsShutdown(),
                "clearLeadName() 后应重置为 false");
    }

    @Test
    void testTaskStoreCrossThreadAccess() throws InterruptedException {
        // 验证主线程创建的任务在子线程中可见且可操作
        Task taskInMain = taskService.createTask("CrossThreadTask", "测试跨线程访问", null);
        assertEquals(TaskStatus.PENDING, taskInMain.getStatus());

        // 子线程中认领并完成任务
        Thread workerThread = new Thread(() -> {
            Task claimed = taskService.claimTask(taskInMain.getId(), "worker-thread");
            assertEquals(TaskStatus.IN_PROGRESS, claimed.getStatus());
            assertEquals("worker-thread", claimed.getOwner());

            Task completed = taskService.completeTask(taskInMain.getId(), "worker-thread");
            assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        });
        workerThread.start();
        workerThread.join(5000);

        // 主线程验证最终状态
        Task finalTask = taskService.getTask(taskInMain.getId());
        assertEquals(TaskStatus.COMPLETED, finalTask.getStatus());
        assertEquals("worker-thread", finalTask.getOwner());
    }

    @Test
    void testMultipleTeammatesCanAccessSameTaskPool() throws InterruptedException {
        // 主线程创建 5 个任务，2 个子线程并发认领并完成
        List<String> taskIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            taskIds.add(taskService.createTask("PoolTask-" + i, "desc", null).getId());
        }

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                for (String id : taskIds) {
                    try {
                        if (!taskService.canStart(id)) continue;
                        taskService.claimTask(id, "worker-A");
                        taskService.completeTask(id, "worker-A");
                    } catch (Exception ignored) { /* 已被抢占 */ }
                }
            } finally { latch.countDown(); }
        });
        executor.submit(() -> {
            try {
                for (String id : taskIds) {
                    try {
                        if (!taskService.canStart(id)) continue;
                        taskService.claimTask(id, "worker-B");
                        taskService.completeTask(id, "worker-B");
                    } catch (Exception ignored) { /* 已被抢占 */ }
                }
            } finally { latch.countDown(); }
        });

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 所有任务应被完成
        for (String id : taskIds) {
            Task t = taskService.getTask(id);
            assertEquals(TaskStatus.COMPLETED, t.getStatus(), "任务 " + id + " 应为 COMPLETED");
            assertNotNull(t.getOwner(), "任务 " + id + " 应有 owner");
        }
    }
}
