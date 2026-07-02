package com.kaer.event.listener;

import com.kaer.agent.JChatMind;
import com.kaer.agent.JChatMindFactory;
import com.kaer.event.ChatEvent;
import com.kaer.message.SseMessage;
import com.kaer.service.impl.SseServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.kaer.agent.ConstantPrompt.LOCK_PREFIX;
import static com.kaer.message.SseMessage.Type.AI_THINKING;

/**
 * 聊天事件监听器
 * <p>
 * 负责监听 {@link ChatEvent} 聊天事件，当有新的聊天消息创建时触发异步处理。
 * 通过 Redis 分布式锁（Redisson）保证同一会话的请求串行执行，
 * 防止并发请求导致 tool_calls/tool_result 消息链断裂。
 */
@Slf4j
@Component
@AllArgsConstructor
public class ChatEventListener {

    private final JChatMindFactory jChatMindFactory;
    private final RedissonClient redissonClient;
    private final SseServiceImpl sseService;

    /**
     * 处理聊天事件（会话级互斥）。
     * <p>
     * 使用 Redis 分布式锁按 sessionId 串行化，同一会话的后续请求
     * 会阻塞等待前一个 Agent 执行完毕。Watchdog 自动续期，无需担心锁超时。
     *
     * @param event 聊天事件对象，包含 AgentId、SessionId 和消息内容
     */
    @Async
    @EventListener
    public void handle(ChatEvent event) {
// ...
        String lockKey = LOCK_PREFIX + event.getSessionId();
        RLock lock = redissonClient.getLock(lockKey);

        // 尝试获取锁，等待 0 秒。如果拿不到，说明有正在处理的任务，直接返回 false
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[ChatEvent] 会话繁忙，拒绝并发请求: sessionId={}", event.getSessionId());
                // TODO: 可选 - 在这里抛出异常或发送 WebSocket 消息通知前端 "请等待回复完成"
                // 完整构建 SseMessage 对象
                SseMessage message = SseMessage.builder()
                        .type(AI_THINKING)
                        .payload(SseMessage.Payload.builder()
                                .statusText("请等待上一个回复完成，再继续提问！")
                                // 这里设为 true 是为了防止前端一直处于 loading 状态
                                .done(true)
                                .build())
                        .build();
                sseService.send(event.getSessionId(), message);
                return;
            }

            log.debug("[ChatEvent] 获取会话锁: sessionId={}", event.getSessionId());
            JChatMind jChatMind = jChatMindFactory.create(event.getAgentId(), event.getSessionId());
            jChatMind.run();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("尝试获取锁时被中断", e);
        } finally {
            // 只有当前线程持有锁时才释放
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[ChatEvent] 释放会话锁: sessionId={}", event.getSessionId());
            }
        }
    }
}