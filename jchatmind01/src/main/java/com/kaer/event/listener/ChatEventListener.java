package com.kaer.event.listener;

import com.kaer.event.ChatEvent;
import com.kaer.agent.JChatMind;
import com.kaer.agent.JChatMindFactory;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 聊天事件监听器
 * <p>
 * 负责监听 {@link ChatEvent} 聊天事件，当有新的聊天消息创建时触发异步处理。
 * 通过工厂模式创建对应的 Agent 实例来处理具体的聊天逻辑。
 */
@Component
@AllArgsConstructor
public class ChatEventListener {

    /**
     * JChatMind 工厂，用于根据 AgentId 和 SessionId 创建对应的 Agent 实例
     */
    private final JChatMindFactory jChatMindFactory;

    /**
     * 处理聊天事件
     * <p>
     * 该方法使用 {@code @Async} 注解标记为异步执行，避免阻塞主线程。
     * 通过工厂创建对应的 Agent 实例并执行聊天处理逻辑。
     * 
     * @param event 聊天事件对象，包含 AgentId、SessionId 和消息内容
     */
    @Async
    @EventListener
    public void handle(ChatEvent event) {
        // 通过工厂创建 Agent 实例，传入 AgentId 和 SessionId
        JChatMind jChatMind = jChatMindFactory.create(event.getAgentId(), event.getSessionId());
        // 执行 Agent 的核心处理逻辑
        jChatMind.run();
    }
}