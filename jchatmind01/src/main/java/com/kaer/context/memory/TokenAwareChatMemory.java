package com.kaer.context.memory;

import com.kaer.context.token.TokenCounter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 感知的聊天记忆，替代 Spring AI 的 MessageWindowChatMemory。
 * 内部存储全部消息，对外提供按 token 预算获取消息子集的能力。
 */
@Component
public class TokenAwareChatMemory implements ChatMemory {

    private final Map<String, List<Message>> store = new ConcurrentHashMap<>();
    private final TokenCounter tokenCounter = new TokenCounter();

    @Override
    public void add(String conversationId, Message message) {
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
    }

    @Override
    public List<Message> get(String conversationId) {
        return new ArrayList<>(store.getOrDefault(conversationId, List.of()));
    }

    /**
     * 获取最近 lastN 条消息（按 token 预算前向后兼容）。
     */
    public List<Message> get(String conversationId, int lastN) {
        List<Message> all = store.getOrDefault(conversationId, List.of());
        if (all.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, all.size() - lastN);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    @Override
    public void clear(String conversationId) {
        store.remove(conversationId);
    }

    /**
     * 获取全部消息。
     */
    public List<Message> getAll(String conversationId) {
        return Collections.unmodifiableList(
                store.getOrDefault(conversationId, List.of())
        );
    }

    /**
     * 按 token 预算获取消息子集，从最新消息往前取。
     * 系统消息始终保留（排在前面），其余按 token 预算从新到旧截断。
     */
    public List<Message> getByTokenBudget(String conversationId, int tokenBudget) {
        List<Message> all = store.getOrDefault(conversationId, List.of());
        if (all.isEmpty()) {
            return List.of();
        }

        List<Message> systemMessages = new ArrayList<>();
        List<Message> otherMessages = new ArrayList<>();
        for (Message msg : all) {
            if (msg instanceof SystemMessage) {
                systemMessages.add(msg);
            } else {
                otherMessages.add(msg);
            }
        }

        int systemTokens = tokenCounter.estimateTokens(systemMessages);
        int available = tokenBudget - systemTokens;
        if (available <= 0) {
            return systemMessages;
        }

        List<Message> result = new ArrayList<>(systemMessages);
        List<Message> kept = new ArrayList<>();
        int running = 0;
        for (int i = otherMessages.size() - 1; i >= 0; i--) {
            Message msg = otherMessages.get(i);
            int mt = tokenCounter.estimateTokens(msg);
            if (running + mt <= available) {
                kept.add(0, msg);
                running += mt;
            } else {
                break;
            }
        }
        result.addAll(kept);
        return result;
    }

    /**
     * 用新的消息列表替换指定会话的全部存储。
     */
    public void replace(String conversationId, List<Message> messages) {
        store.put(conversationId, new ArrayList<>(messages));
    }

    public int estimateTotalTokens(String conversationId) {
        return tokenCounter.estimateTokens(
                store.getOrDefault(conversationId, List.of())
        );
    }
}
