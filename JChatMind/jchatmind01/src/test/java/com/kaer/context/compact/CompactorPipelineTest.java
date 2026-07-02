package com.kaer.context.compact;

import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.token.TokenCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压缩流水线集成测试。
 *
 * <p>验证 L3→L2→L1→L4 执行顺序和各层短路退出。
 */
@DisplayName("压缩流水线集成")
class CompactorPipelineTest {

    private CompactorPipeline pipeline;
    private TokenCounter tokenCounter;
    private TokenAwareChatMemory chatMemory;

    @BeforeEach
    void setUp() throws Exception {
        tokenCounter = new TokenCounter();
        chatMemory = new TokenAwareChatMemory();

        // 手动组装 pipeline（L2 现使用 Redis 缓存，测试中以 null 注入）
        L3SingleToolResponseGate l3 = new L3SingleToolResponseGate(
                new com.kaer.context.truncator.ContextTruncator(
                        tokenCounter,
                        new com.kaer.context.cache.ToolResponseCacheInterceptor(
                                null,  // cacheService = null for test
                                new com.kaer.context.truncator.CacheTruncationSuffixBuilder(tokenCounter),
                                tokenCounter)),
                tokenCounter);

        L2HistoricalToolResultCleanup l2 = new L2HistoricalToolResultCleanup(tokenCounter, null);
        L1SlidingWindowTrim l1 = new L1SlidingWindowTrim(tokenCounter);
        L4FullSummaryFallback l4 = new L4FullSummaryFallback(tokenCounter);

//        pipeline = new CompactorPipeline(l/*/3, l2, l1, l4, tokenCounter,new MemoryNoteStoreServiceImpl());
    }

    private CompactorContext ctx(int tokenBudget) {
        return CompactorContext.builder()
                .sessionId("test-session")
                .tokenBudget(tokenBudget)
                .config(defaultConfig())
                .tokenCounter(tokenCounter)
                .chatMemory(chatMemory)
                .build();
    }

    private com.kaer.model.dto.AgentDTO.ChatOptions defaultConfig() {
        return com.kaer.model.dto.AgentDTO.ChatOptions.builder()
                .compactL1KeepFirstK(3)
                .compactL1KeepLastN(5)
                .compactL2MaxKeptResults(3)
                .compactL4Enabled(true)
                .maxToolResponseTokens(4000)
                .build();
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("消息少，预算内 → 全管道通过无修改")
    void smallMessagesAllPassThrough() {
        List<Message> msgs = List.of(
                new UserMessage("hi"));

        CompactResult result = pipeline.compact(msgs, ctx(100_000));

        // 所有级别应保持消息不变
        assertEquals(1, result.getMessages().size());
        // L3 满足预算后短路
        assertTrue(result.isBudgetSatisfied());
    }

    @Test
    @DisplayName("大量消息 → 流水线逐步压缩")
    void manyMessagesGoesThroughLevels() {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            msgs.add(new UserMessage("Message number " + i
                    + " with some additional padding text to consume more tokens"));
        }

        // 设定一个较小的预算，确保 L1 被触发
        int allTokens = tokenCounter.estimateTokens(msgs);
        int tightBudget = allTokens / 5;

        CompactResult result = pipeline.compact(msgs, ctx(tightBudget));

        // 压缩后消息数应少于原始
        assertTrue(result.getMessages().size() < msgs.size(),
                "应至少经过一级压缩");
    }

    @Test
    @DisplayName("超大预算 → L3 即满足，短路退出")
    void hugeBudgetExitsAtL3() {
        List<Message> msgs = List.of(
                new UserMessage("hello"),
                new UserMessage("world"));

        CompactResult result = pipeline.compact(msgs, ctx(Integer.MAX_VALUE));

        assertEquals("L3", result.getAction());
        assertTrue(result.isBudgetSatisfied());
    }

    @Test
    @DisplayName("空消息列表 → 正常处理")
    void emptyMessages() {
        CompactResult result = pipeline.compact(List.of(), ctx(100_000));
        assertNotNull(result);
        assertTrue(result.getMessages().isEmpty());
    }
}
