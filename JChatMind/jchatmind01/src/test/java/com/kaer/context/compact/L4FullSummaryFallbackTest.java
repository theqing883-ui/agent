package com.kaer.context.compact;

import com.kaer.agent.ConstantPrompt;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.token.TokenCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * L4 全量摘要兜底单元测试。
 */
@DisplayName("L4 LLM 全量摘要")
@ExtendWith(MockitoExtension.class)
class L4FullSummaryFallbackTest {

    private L4FullSummaryFallback l4;
    private TokenCounter tokenCounter;
    private TokenAwareChatMemory chatMemory;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
        chatMemory = new TokenAwareChatMemory();
        l4 = new L4FullSummaryFallback(tokenCounter);
    }

    private CompactorContext ctx(int tokenBudget, boolean l4Enabled) {
        return CompactorContext.builder()
                .sessionId("test-session")
                .tokenBudget(tokenBudget)
                .tokenCounter(tokenCounter)
                .chatMemory(chatMemory)
                .cheapChatClient(chatClient)
                .config(com.kaer.model.dto.AgentDTO.ChatOptions.builder()
                        .compactL4Enabled(l4Enabled)
                        .build())
                .build();
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("L4 禁用 → 跳过，返回 continue")
    void skipWhenL4Disabled() {
        List<Message> msgs = List.of(
                new UserMessage("hello"),
                new UserMessage("world"));

        CompactResult result = l4.compact(msgs, ctx(10, false));

        assertFalse(result.isBudgetSatisfied());
        assertEquals("L4", result.getAction());
        assertEquals(2, result.getMessages().size());
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("Token 在预算内 → 跳过 L4，返回 pass")
    void skipWhenWithinBudget() {
        // "hi" ≈ 1 token，在 100000 预算内
        List<Message> msgs = List.of(new UserMessage("hi"));

        CompactResult result = l4.compact(msgs, ctx(100000, true));

        assertTrue(result.isBudgetSatisfied());
        assertEquals("L4", result.getAction());
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("Token 超出预算 → 触发 LLM 摘要，替换 chatMemory")
    void triggersWhenOverBudget() {
        // 构建大消息列表，token 一定超过 10
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("This is a long test message number " + i
                    + " with many extra words to consume tokens"));
        }
        // 先存入 chatMemory
        chatMemory.add("test-session", msgs);

        // Mock ChatClient 链式调用
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                1. 当前目标：运行测试
                2. 关键决定：使用 Mockito 模拟 LLM
                3. 变更文件：L4FullSummaryFallbackTest.java
                4. 待办事项：验证测试通过
                5. 用户约束：无""");

        CompactResult result = l4.compact(msgs, ctx(10, true));

        assertTrue(result.isBudgetSatisfied());
        assertTrue(result.isMemoryReplaced());
        assertEquals("L4", result.getAction());
        assertEquals(2, result.getMessages().size(),
                "应为 1条摘要 SystemMessage + 1条最后 UserMessage");

        // 验证 chatMemory 已被替换
        List<Message> memoryMsgs = chatMemory.getAll("test-session");
        assertEquals(2, memoryMsgs.size());
    }

    @Test
    @DisplayName("LLM 调用异常 → 降级返回原始消息")
    void fallbackOnLLMFailure() {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("A long message " + i + " with padding to increase token count substantially"));
        }

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("API 调用失败"));

        CompactResult result = l4.compact(msgs, ctx(10, true));

        assertFalse(result.isBudgetSatisfied(),
                "LLM 失败时应返回 budgetSatisfied=false");
        assertEquals("L4", result.getAction());
        assertFalse(result.isMemoryReplaced());
        assertEquals(msgs.size(), result.getMessages().size(),
                "应返回原始消息不变");
    }

    @Test
    @DisplayName("摘要为空 → 降级返回原始消息")
    void emptySummaryFallback() {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("Long message " + i + " with enough text to exceed a small token budget easily"));
        }

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("");  // 空摘要

        CompactResult result = l4.compact(msgs, ctx(10, true));

        assertFalse(result.isBudgetSatisfied());
        assertEquals("L4", result.getAction());
    }

    @Test
    @DisplayName("没有 UserMessage → 摘要后只包含 SystemMessage")
    void noUserMessageInHistory() {
        // 只有 AssistantMessage 没有 UserMessage
        // 需要足够多的消息让 token 超过预算（预算设为极小值）
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            msgs.add(new org.springframework.ai.chat.messages.AssistantMessage(
                    "Bot response number " + i + " with some padding"));
        }

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                1. 当前目标：无
                2. 关键决定：无
                3. 变更文件：无
                4. 待办事项：无
                5. 用户约束：无""");

        // 预算设极小值确保触发 L4
        CompactResult result = l4.compact(msgs, ctx(5, true));

        assertTrue(result.isBudgetSatisfied());
        // 只有摘要 SystemMessage，没有 UserMessage（因为原始消息中没有 UserMessage）
        assertEquals(1, result.getMessages().size());
    }
}
