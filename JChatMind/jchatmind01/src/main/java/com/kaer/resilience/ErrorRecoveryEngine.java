package com.kaer.resilience;

import com.kaer.agent.AgentContextHolder;
import com.kaer.config.ChatClientRegistry;
import com.kaer.context.memory.TokenAwareChatMemory;
import com.kaer.context.token.TokenCounter;
import com.kaer.exception.MaxRecoveryExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.kaer.agent.ConstantPrompt.SUMMARIZE_ERR_SYSTEM_PROMPT;
import static com.kaer.agent.ConstantPrompt.SUMMARIZE_ERR_USER_PROMPT;

/**
 * LLM 调用层高可用错误捕获与自愈引擎。
 *
 * <p><b>定位：</b>Spring AI 内置重试之后的<b>第二层防线</b>。
 * 第一层（{@link com.kaer.config.ResilienceRetryConfig} 定制的 RetryTemplate）
 * 仅处理网络层 I/O 瞬断；本引擎接管所有业务级 / 限流级异常，执行语义级自愈。
 *
 * <p><b>三大自愈策略：</b>
 * <ol>
 *   <li><b>临时故障与限流（429 / 529）：</b>指数退避 + 随机 Jitter，529 连续 3 次自动切换备胎模型。</li>
 *   <li><b>输出截断（Max Tokens）：</b>第一道防线无痕升级到 64K 重试；第二道防线续写提示（最多 3 次）。</li>
 *   <li><b>上下文超限（Prompt Too Long）：</b>剥离最后一条长消息，摘要压缩历史，重组后重试（仅 1 次）。</li>
 * </ol>
 *
 * <p><b>线程安全：</b>所有状态绑定在 {@link RecoveryState}（AgentContextHolder ThreadLocal 中），
 * 天然线程隔离。每个 Agent 线程拥有独立的恢复上下文。
 *
 * <p><b>ThreadLocal 防污染：</b>{@link #executeWithRecovery} 使用严格
 * {@code try-finally} 结构，无论成功/异常都执行 {@link AgentContextHolder#clearRecoveryState()}，
 * 防止 Tomcat 线程池复用时的状态泄漏。
 *
 * @see ResilienceConfig
 * @see RecoveryState
 * @see MaxRecoveryExceededException
 */
@Slf4j
@Component
public class ErrorRecoveryEngine {

    private final ChatClientRegistry chatClientRegistry;
    private final ResilienceConfig config;

    public ErrorRecoveryEngine(ChatClientRegistry chatClientRegistry,
                               ResilienceConfig config) {
        this.chatClientRegistry = chatClientRegistry;
        this.config = config;
    }

    // ========================================================================
    // 公开入口
    // ========================================================================

    /**
     * 带韧性恢复的 LLM 调用入口。
     *
     * <p><b>调用方（JChatMind.think()）使用示例：</b>
     * <pre>{@code
     * this.lastChatResponse = errorRecoveryEngine.executeWithRecovery(
     *     this.chatClient, prompt, thinkPrompt, this.availableTools,
     *     chatMemory, chatSessionId, this.modelName);
     * }</pre>
     *
     * @param primaryClient  当前首选 ChatClient
     * @param originalPrompt 原始 Prompt（含 ChatOptions 和 Messages）
     * @param systemPrompt   系统提示词文本
     * @param toolCallbacks  已注册的工具回调列表
     * @param chatMemory     Token 感知的聊天记忆（用于续写时追加消息）
     * @param chatSessionId  会话 ID（用于日志）
     * @param modelName      当前模型名称（用于备胎切换查找）
     * @return LLM 返回的 ChatResponse（已完成所有自愈尝试）
     * @throws MaxRecoveryExceededException 当恢复尝试超过配置上限时抛出
     * @throws RuntimeException             当遇到无法分类的未知异常时直接抛出
     */
    public ChatResponse executeWithRecovery(
            ChatClient primaryClient,
            Prompt originalPrompt,
            String systemPrompt,
            List<ToolCallback> toolCallbacks,
            TokenAwareChatMemory chatMemory,
            String chatSessionId,
            String modelName
    ) {
        // ── 1. 初始化/获取 ThreadLocal RecoveryState ──
        RecoveryState state = AgentContextHolder.getRecoveryState();
        state.setOriginalModel(modelName);
        state.setCurrentModel(modelName);

        ChatClient currentClient = primaryClient;
        Prompt currentPrompt = originalPrompt;

        try {
            // ── 2. 自愈主循环 ──
            while (true) {
                try {
                    // 执行 LLM 调用
                    ChatResponse response = currentClient.prompt(currentPrompt)
                            .system(systemPrompt)
                            .toolCallbacks(toolCallbacks)
                            .call()
                            .chatClientResponse()
                            .chatResponse();

                    // ── 成功返回后检查 Max Tokens 截断 ──
                    if (isTruncatedByMaxTokens(response)) {
                        log.warn("[Resilience] Max tokens 截断 detected (finishReason=length/max_tokens), "
                                        + "session={}, continuationRetryCount={}",
                                chatSessionId, state.getContinuationRetryCount());

                        // 处理截断（内部可能重试或抛出 MaxRecoveryExceededException）
                        currentPrompt = handleMaxTokensTruncation(
                                currentClient, currentPrompt, systemPrompt,
                                toolCallbacks, chatMemory, chatSessionId, state, response);
                        // handleMaxTokensTruncation 返回新 Prompt 后继续循环重试
                        continue;
                    }

                    // ── 429重试成功 重置次数 ──
                    state.setTransientRetryCount(0);

                    // 无痕拼接（Response Stitching）：
                    // 如果发生过截断续写，将累加器中的残骸与本次完整响应拼接，
                    // 向上层返回一个"从未截断过"的完整 ChatResponse
                    if (state.hasAccumulatedText()) {
                        log.info("[Resilience] 无痕拼接: 累加残骸={}chars + 本次响应={}chars, session={}",
                                state.getAccumulatedText().length(),
                                extractAssistantText(response) != null
                                        ? extractAssistantText(response).length() : 0,
                                chatSessionId);
                        return stitchResponse(response, state);
                    }
                    return response;

                } catch (MaxRecoveryExceededException e) {
                    // 直接向上传播
                    throw e;

                } catch (Exception e) {
                    // ── 异常分类与自愈 ──
                    ErrorType errorType = classifyException(e);

                    log.warn("[Resilience] 捕获异常: type={}, session={}, model={}, message={}",
                            errorType, chatSessionId, state.getCurrentModel(),
                            e.getMessage());

                    switch (errorType) {
                        case FATAL_INTERRUPTED -> {
                            log.warn("[Resilience] 线程被中断，放弃重试: session={}", chatSessionId);
                            throw new RuntimeException(
                                    "[Resilience] 线程被中断，终止 Agent 执行", e);
                        }
                        case OVERLOADED_529 -> {
                            currentClient = handle529(e, currentClient, chatSessionId, state);
                            // 529 退避后继续循环，使用（可能已切换的）Client 重试
                        }
                        case RATE_LIMITED_429 -> {
                            handle429(e, chatSessionId, state);
                            // 429 退避后继续循环，使用原 Client 重试
                        }
                        case CONTEXT_LENGTH_EXCEEDED -> {
                            currentPrompt = handleContextLengthExceeded(
                                    e, currentClient, currentPrompt, systemPrompt,
                                    toolCallbacks, chatSessionId, state);
                            // 重组 Prompt 后继续循环重试
                        }
                        default -> {
                            log.error("[Resilience] 未分类异常，无法自愈，向上传播: session={}, model={}",
                                    chatSessionId, state.getCurrentModel(), e);
                            throw new RuntimeException(
                                    "[Resilience] 未分类的 LLM 调用异常: " + e.getMessage(), e);
                        }
                    }
                }
            } // end while
        } finally {
            // ── 3. 强制清理 RecoveryState，防止线程池污染（至关重要！）──
            AgentContextHolder.clearRecoveryState();
        }
    }

    // ========================================================================
    // 策略 1: 429 Rate Limited —— 指数退避 + Jitter
    // ========================================================================

    /**
     * 处理 429 Too Many Requests。
     *
     * <p>使用指数退避 + 随机 Jitter 避免并发雪崩：
     * <pre>
     *   delay = min(baseDelayMs * 2^attempt, maxDelayMs)
     *   jitter = delay * random(0, jitterRatio)
     *   sleep(delay + jitter)
     * </pre>
     *
     * @param e         原始异常
     * @param sessionId 会话 ID
     * @param state     当前恢复状态（会更新 transientRetryCount）
     */
    private void handle429(Exception e, String sessionId, RecoveryState state) {
        int attempt = state.getTransientRetryCount();
        long delay = computeBackoffDelay(attempt);

        log.warn("[Resilience] 429 Rate Limited —— 第 {} 次退避, delay={}ms, session={}",
                attempt + 1, delay, sessionId);

        sleepQuietly(delay);

        state.setTransientRetryCount(attempt + 1);
    }

    // ========================================================================
    // 策略 1b: 529 Overloaded —— 指数退避 + Jitter + 备胎模型切换
    // ========================================================================

    /**
     * 处理 529 Overloaded。
     *
     * <p>与 429 相同的指数退避 + Jitter，并额外追踪连续 529 次数。
     * 连续 3 次 529 后自动切换为备胎模型（如 deepseek-chat → glm-4.6）。
     *
     * @param e         原始异常
     * @param client    当前 ChatClient
     * @param sessionId 会话 ID
     * @param state     当前恢复状态
     * @return 可能已切换的 ChatClient（如果触发了备胎切换）
     */
    private ChatClient handle529(Exception e, ChatClient client, String sessionId,
                                 RecoveryState state) {
        int attempt = state.getConsecutive529Count();
        long delay = computeBackoffDelay(attempt);

        // 更新 529 计数和时间戳
        state.setConsecutive529Count(attempt + 1);
        state.setLast529Timestamp(System.currentTimeMillis());

        // 检查是否需要备胎切换
        int threshold = config.getBackoff().getFallback529Threshold();
        if (state.shouldFallback(threshold)) {
            // 因为只有两个模型，第二次进行模型切换的时候，就直接抛异常
            if (state.isFallbackActive()) {
                log.error("[Resilience] 备胎模型 [{}] 也连续 {} 次发生 529 过载，放弃抢救，触发全局熔断！session={}",
                        state.getCurrentModel(), threshold, sessionId);
                // 抛出超过最大恢复次数异常，阻断 while(true) 循环
                throw new MaxRecoveryExceededException(
                        "Fallback model (" + state.getCurrentModel() + ") also overloaded", attempt);
            }

            String fallbackModel = config.getFallbackModel(state.getCurrentModel());
            if (fallbackModel != null) {
                ChatClient fallbackClient = chatClientRegistry.get(fallbackModel);
                if (fallbackClient != null) {
                    log.warn("[Resilience] 529 连续 {} 次，触发备胎切换: {} → {}, session={}",
                            state.getConsecutive529Count(),
                            state.getCurrentModel(), fallbackModel, sessionId);

                    state.setCurrentModel(fallbackModel);
                    state.setFallbackActive(true);
                    state.setConsecutive529Count(0); // 重置计数

                    sleepQuietly(delay);
                    return fallbackClient;
                } else {
                    log.error("[Resilience] 备胎模型 {} 未注册 ChatClient，无法切换，session={}",
                            fallbackModel, sessionId);
                }
            } else {
                log.warn("[Resilience] 模型 {} 未配置备胎，无法切换，session={}",
                        state.getCurrentModel(), sessionId);
            }
        }

        log.warn("[Resilience] 529 Overloaded —— 第 {} 次退避, delay={}ms, session={}, model={}",
                state.getConsecutive529Count(), delay, sessionId, state.getCurrentModel());

        sleepQuietly(delay);
        return client; // 未切换，返回原 Client
    }

    // ========================================================================
    // 策略 2: Max Tokens 截断 —— 无痕升级 + 续写
    // ========================================================================

    /**
     * 处理输出截断（finishReason = "length" / "max_tokens"）。
     *
     * <p><b>第一道防线（无痕升级，continuationRetryCount == 0）：</b>
     * <ul>
     *   <li>不将残缺的 AssistantMessage 加入 chatMemory（不污染对话历史）</li>
     *   <li>构建全新 Prompt，max_tokens 升级到 64K</li>
     *   <li>保持原 Messages 完全不变，重新发起调用</li>
     * </ul>
     *
     * <p><b>第二道防线（续写提示，continuationRetryCount >= 1）：</b>
     * <ul>
     *   <li>将截断文本存入 RecoveryState 累加器（绝不触碰 chatMemory）</li>
     *   <li>基于 originalPrompt.getInstructions()（上层已裁剪的安全上下文）重建消息列表</li>
     *   <li>追加累加后的完整残骸 AssistantMessage + 续写指令 SystemMessage</li>
     *   <li>重建 Prompt 包含续写上下文后重试</li>
     * </ul>
     *
     * <p><b>Token 爆炸防护：</b>重建 Prompt 时严格基于
     * {@code originalPrompt.getInstructions()}——即上层 ContextWindowManager
     * 已经裁剪好的安全上下文，绝不使用 chatMemory.getAll() 全量导入。
     *
     * <p><b>记忆污染防护：</b>绝对禁止调用 {@code chatMemory.add()}。
     * 残缺文本和续写指令仅存在于本次重试 Prompt 中，对上层透明。
     *
     * <p><b>熔断：</b>续写超过 {@code config.continuation.maxRetries}（默认 3）次后抛出
     * {@link MaxRecoveryExceededException}。
     *
     * @return 用于重试的新 Prompt（已更新 ChatOptions 或 Messages）
     * @throws MaxRecoveryExceededException 续写次数超限
     */
    private Prompt handleMaxTokensTruncation(
            ChatClient client,
            Prompt originalPrompt,
            String systemPrompt,
            List<ToolCallback> toolCallbacks,
            TokenAwareChatMemory chatMemory,
            String chatSessionId,
            RecoveryState state,
            ChatResponse truncatedResponse
    ) {
        int retryCount = state.getContinuationRetryCount();
        state.setContinuationRetryCount(retryCount + 1);

        if (retryCount == 0) {
            // ===== 第一道防线：无痕升级 max_tokens 到 64K =====
            log.info("[Resilience] 第一道防线（无痕升级）: max_tokens → {} , session={}",
                    config.getContinuation().getUpgradedTokens(), chatSessionId);

            // 关键：不将残缺文本加入 chatMemory，不存入累加器，直接重建 Prompt
            // 使用 Builder 复制原 ChatOptions 并设置新的 max_tokens（绝对不修改原对象！）
            ChatOptions upgradedOpts = DefaultToolCallingChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .maxTokens(config.getContinuation().getUpgradedTokens())
                    .build();

            return Prompt.builder()
                    .chatOptions(upgradedOpts)
                    .messages(originalPrompt.getInstructions())  // 原 Messages 不变
                    .build();

        } else if (retryCount <= config.getContinuation().getMaxRetries()) {
            // ===== 第二道防线：续写提示 =====
            log.warn("[Resilience] 第二道防线（续写提示）: retry={}/{}, session={}",
                    retryCount, config.getContinuation().getMaxRetries(), chatSessionId);

            // ──  从截断响应中提取残缺文本，存入累加器（不污染 chatMemory）──
            String truncatedText = extractAssistantText(truncatedResponse);
            state.appendText(truncatedText);
            log.debug("[Resilience] 截断文本已存入累加器: {} chars, 累计: {} chars, session={}",
                    truncatedText != null ? truncatedText.length() : 0,
                    state.getAccumulatedText().length(), chatSessionId);

            // ── 基于 originalPrompt.getInstructions() 重建消息列表 ──
            // 复用上层 ContextWindowManager 已裁剪好的安全上下文，防止 Token 爆炸
            List<Message> updatedMessages = new ArrayList<>(
                    originalPrompt.getInstructions());

            // ── 将累加器中的完整残骸构造成 AssistantMessage 追加 ──
            String accumulated = state.getAccumulatedText();
            if (!accumulated.isEmpty()) {
                updatedMessages.add(AssistantMessage.builder()
                        .content(accumulated)
                        .build());
            }

            // ──  追加续写指令 SystemMessage ──
            updatedMessages.add(new SystemMessage(
                    "触发输出 Token 上限。直接续写 —— 无需道歉，无需回顾。紧接中断处的思路继续。"
            ));

            // ── 动态重建 Prompt（copy 新 ChatOptions，不修改原对象）──
            ChatOptions continuationOpts = DefaultToolCallingChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .maxTokens(config.getContinuation().getUpgradedTokens())
                    .build();

            log.debug("[Resilience] 续写 Prompt 已重建: 原消息={}条, 累加残骸={}chars, session={}",
                    originalPrompt.getInstructions().size(), accumulated.length(), chatSessionId);

            return Prompt.builder()
                    .chatOptions(continuationOpts)
                    .messages(updatedMessages)
                    .build();

        } else {
            // ===== 熔断：超过最大续写次数 =====
            throw new MaxRecoveryExceededException("continuation",
                    state.getContinuationRetryCount());
        }
    }

    // ========================================================================
    // 策略 3: 上下文超限 —— 降级摘要
    // ========================================================================

    /**
     * 处理上下文超限（Context Length Exceeded）。
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>从 Messages 列表中剥离最后一条消息（引发超限的"最后一击"）。</li>
     *   <li><b>消息配对保护：</b>如果最后一条是 ToolResponseMessage，且前一条是带 tool_calls
     *       的 AssistantMessage，则成对剥离，防止对话链断裂。</li>
     *   <li>对剩余消息中较早的部分（排除最近 N 条）调用 LLM 生成长度精炼的摘要。</li>
     *   <li>重新组装：[摘要 SystemMessage] + [最近 N 条] + [剥离的长消息（对）]。</li>
     *   <li>使用重组后的 Messages 构建新 Prompt 重试。</li>
     * </ol>
     *
     * <p><b>限制：</b>此流程仅执行 1 次，若再次超限直接抛出异常。
     *
     * @return 用于重试的新 Prompt（消息列表已被重组）
     * @throws RuntimeException 摘要降级后再次超限
     */
    private Prompt handleContextLengthExceeded(
            Exception e,
            ChatClient currentClient,
            Prompt originalPrompt,
            String systemPrompt,
            List<ToolCallback> toolCallbacks,
            String chatSessionId,
            RecoveryState state
    ) {
        // 仅允许 1 次降级
        if (state.isContextLengthAlreadyRetried()) {
            log.error("[Resilience] 上下文超限摘要降级已执行过，再次超限，放弃自愈: session={}",
                    chatSessionId);
            throw new RuntimeException(
                    "[Resilience] 上下文超限降级后再次超限，无法自动恢复", e);
        }
        state.setContextLengthRetried(true);

        List<Message> originalMessages = new ArrayList<>(originalPrompt.getInstructions());
        int totalSize = originalMessages.size();

        log.warn("[Resilience] 上下文超限 detected, 消息总数={}, session={}, 开始摘要降级...",
                totalSize, chatSessionId);

        // ── 1. 剥离"最后一击"（保持消息配对）──
        int stripCount = computeStripCount(originalMessages);
        List<Message> lastStraw = new ArrayList<>();
        for (int i = 0; i < stripCount; i++) {
            lastStraw.add(0, originalMessages.remove(originalMessages.size() - 1));
        }
        log.info("[Resilience] 剥离最后 {} 条消息（保持角色配对）, 剩余 {} 条, session={}",
                stripCount, originalMessages.size(), chatSessionId);

        // ── 2. 分离"最近关键对话"与"待摘要历史" ──
        int reservedCount = config.getContextLength().getReservedRecentMessages();
        // 实际保留数 = min(配置值, 剩余消息数)
        int actualReserved = Math.min(reservedCount, originalMessages.size());

        // 从剩余消息尾部取出最近 N 条作为关键对话
        List<Message> recentKey = new ArrayList<>();
        if (actualReserved > 0) {
            int splitIdx = originalMessages.size() - actualReserved;
            // 调整分割点以保证角色配对（见 ensurePairingBoundary）
            splitIdx = ensurePairingBoundary(originalMessages, splitIdx);
            recentKey = new ArrayList<>(
                    originalMessages.subList(splitIdx, originalMessages.size()));
            // 待摘要的旧历史
            originalMessages = new ArrayList<>(
                    originalMessages.subList(0, splitIdx));
        }

        log.info("[Resilience] 待摘要历史: {} 条, 保留最近: {} 条, session={}",
                originalMessages.size(), recentKey.size(), chatSessionId);

        // ── 3. 对旧历史生成摘要 ──
        String summary = generateContextSummary(
                currentClient, originalMessages, chatSessionId);

        // ── 4. 重组 Messages ──
        List<Message> rebuilt = new ArrayList<>();

        // 摘要（如果有）
        if (summary != null && !summary.isEmpty()) {
            rebuilt.add(new SystemMessage(
                    "[对话历史摘要（因上下文超限触发降级）]\n" + summary));
        }

        // 最近关键对话
        rebuilt.addAll(recentKey);

        // 最后一条长消息（已保持配对）
        rebuilt.addAll(lastStraw);

        log.info("[Resilience] 上下文重组完成: 摘要={}, 关键对话={}条, 长消息={}条, 总计={}条, session={}",
                summary != null ? summary.length() + "chars" : "无",
                recentKey.size(), lastStraw.size(), rebuilt.size(), chatSessionId);

        // ── 5.  动态重建 Prompt（copy 新 ChatOptions，不修改原对象）──
        ChatOptions rebuiltOpts = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();

        return Prompt.builder()
                .chatOptions(rebuiltOpts)
                .messages(rebuilt)
                .build();
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 检查 ChatResponse 是否因 max_tokens 限制而被截断。
     *
     * <p>检查路径：{@code ChatResponse → Generation → GenerationMetadata → finishReason}。
     * 触发条件：finishReason 为 "length" 或 "max_tokens"（不区分大小写）。
     */
    private boolean isTruncatedByMaxTokens(ChatResponse response) {
        if (response == null) {
            return false;
        }
        // 遍历所有 Generation（通常只有 1 个）
        List<Generation> generations = response.getResults();
        if (generations == null || generations.isEmpty()) {
            return false;
        }
        for (Generation gen : generations) {
            if (gen.getMetadata() != null) {
                String finishReason = gen.getMetadata().getFinishReason();
                if (finishReason != null) {
                    String fr = finishReason.toLowerCase();
                    if ("length".equals(fr) || "max_tokens".equals(fr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 从 ChatResponse 中提取 AssistantMessage 的文本内容。
     */
    private String extractAssistantText(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        AssistantMessage output = response.getResult().getOutput();
        return output != null ? output.getText() : null;
    }

    /**
     * 无痕拼接 —— 将累加器中的截断残骸与本次完整响应的文本拼接，
     * 返回一个"从未截断过"的全新 ChatResponse。
     *
     * <p>为什么不用原始 response 直接改字段：
     * ChatResponse / Generation / AssistantMessage 均为不可变对象（record 或 final class），
     * 必须通过重建来替换文本内容。
     *
     * <p>保留信息：
     * <ul>
     *   <li>本次响应的 toolCalls、media、metadata 全部保留</li>
     *   <li>GenerationMetadata（含最终 finishReason）保留</li>
     *   <li>ChatResponseMetadata（model、id、usage）保留</li>
     * </ul>
     *
     * @param currentResponse 最后一次（不再截断的）完整 ChatResponse
     * @param state           当前 RecoveryState（含累加器残骸）
     * @return 拼接后的完整 ChatResponse
     */
    private ChatResponse stitchResponse(ChatResponse currentResponse, RecoveryState state) {
        String accumulatedText = state.getAccumulatedText();
        String currentText = extractAssistantText(currentResponse);
        String stitchedText = accumulatedText + (currentText != null ? currentText : "");

        // 获取当前输出，保留 toolCalls / media / metadata
        AssistantMessage currentOutput = currentResponse.getResult().getOutput();

        // 构建新的 AssistantMessage：文本为拼接后的完整内容，保留 toolCalls
        AssistantMessage stitchedOutput = AssistantMessage.builder()
                .content(stitchedText)
                .toolCalls(currentOutput.getToolCalls())
                .build();

        //  构建新的 Generation：沿用原 GenerationMetadata（含最终 finishReason）
        List<Generation> stitchedGenerations = new ArrayList<>();
        stitchedGenerations.add(new Generation(
                stitchedOutput,
                currentResponse.getResult().getMetadata()));

        // 构建新的 ChatResponse：沿用原 ChatResponseMetadata
        return new ChatResponse(stitchedGenerations, currentResponse.getMetadata());
    }

    /**
     * 异常分类器 —— 通过检查异常消息和原因链中的关键字符串识别错误类型。
     *
     * <p><b>注意：</b>DeepSeek / ZhipuAI 的 HTTP 错误响应会被 Spring AI 包装为
     * {@code NonTransientAiException} 或 {@code TransientAiException}，
     * 原始状态码和错误消息会出现在异常 message 或 cause chain 中。
     */
    private ErrorType classifyException(Exception e) {
        // 线程中断 —— 优先检测，立即终止，不进入重试循环
        if (isInterruptException(e)) {
            return ErrorType.FATAL_INTERRUPTED;
        }

        String fullMessage = getFullExceptionMessage(e).toLowerCase();

        // 529 Overloaded —— 优先匹配（状态码更具体）
        if (fullMessage.contains("529")
                || fullMessage.contains("overloaded")
                || fullMessage.contains("server overloaded")) {
            return ErrorType.OVERLOADED_529;
        }

        // 429 Rate Limited
        if (fullMessage.contains("429")
                || fullMessage.contains("too many requests")
                || fullMessage.contains("rate limit")
                || fullMessage.contains("rate_limit")) {
            return ErrorType.RATE_LIMITED_429;
        }

        // 400 + Context Length Exceeded（不能仅依赖 400，因为 400 也可能表示其他错误）
        if (fullMessage.contains("context length")
                || fullMessage.contains("maximum context length")
                || fullMessage.contains("input is too long")
                || fullMessage.contains("too many tokens")
                || fullMessage.contains("reduce the length")
                || fullMessage.contains("token limit")
                || (fullMessage.contains("400")
                && (fullMessage.contains("length") || fullMessage.contains("token")))) {
            return ErrorType.CONTEXT_LENGTH_EXCEEDED;
        }

        return ErrorType.UNKNOWN;
    }

    /**
     * 遍历异常 cause chain，检查是否由线程中断引起。
     */
    private boolean isInterruptException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof InterruptedException
                    || current instanceof org.springframework.retry.backoff.BackOffInterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 递归收集异常链中所有 message，用于准确的异常分类。
     */
    private String getFullExceptionMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /**
     * 指数退避延迟计算。
     *
     * @param attempt 当前尝试次数（0-based）
     * @return 退避延迟（毫秒），含 Jitter
     */
    private long computeBackoffDelay(int attempt) {
        long baseDelay = config.getBackoff().getBaseDelayMs();
        long maxDelay = config.getBackoff().getMaxDelayMs();
        double jitterRatio = config.getBackoff().getJitterRatio();

        // delay = baseDelay * (2 ^ attempt)
        long delay = baseDelay * (1L << Math.min(attempt, 10)); // 防止移位溢出
        delay = Math.min(delay, maxDelay);

        // Jitter = delay * random(0, jitterRatio)
        long jitter = (long) (delay * ThreadLocalRandom.current().nextDouble(jitterRatio));

        return delay + jitter;
    }

    /**
     * 安静地 sleep，被中断时恢复中断标志。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[Resilience] 退避等待被中断，终止当前恢复流程");
            throw new RuntimeException("[Resilience] 线程被中断，放弃重试", ie);
        }
    }

    /**
     * 计算需要剥离的最后消息数量，保证角色配对不断裂。
     *
     * <p><b>配对规则：</b>
     * 大模型严格校验 "AssistantMessage(tool_calls) → ToolResponseMessage" 的成对出现。
     * 如果最后一条是 ToolResponseMessage，则前一条带 tool_calls 的 AssistantMessage
     * 必须一起剥离，否则剩余消息中会出现孤立的 AssistantMessage(tool_calls) 没有对应的
     * Tool 返回。
     *
     * @param messages 当前完整消息列表
     * @return 需要剥离的消息数量（1 或 2）
     */
    private int computeStripCount(List<Message> messages) {
        if (messages.isEmpty()) {
            return 0;
        }

        Message last = messages.get(messages.size() - 1);

        // 如果最后一条是 ToolResponseMessage，检查前一条
        if (last instanceof ToolResponseMessage && messages.size() >= 2) {
            Message secondLast = messages.get(messages.size() - 2);
            if (secondLast instanceof AssistantMessage assistMsg
                    && assistMsg.getToolCalls() != null
                    && !assistMsg.getToolCalls().isEmpty()) {
                // 成对剥离：Assistant(tool_calls) + ToolResponse
                return 2;
            }
        }

        // 如果最后一条是带 tool_calls 的 AssistantMessage（极端情况），
        // 单独剥离，因为其后没有对应的 ToolResponse
        return 1;
    }

    /**
     * 调整分割点，确保在消息配对边界上切割，不割裂
     * AssistantMessage(tool_calls) 与其后的 ToolResponseMessage。
     *
     * <p>策略：向前扫描分割点附近的 message，如果分割点恰好落在
     * AssistantMessage(tool_calls) 和 ToolResponseMessage 之间，则将分割点前移 1。
     *
     * @param messages 消息列表
     * @param splitIdx 原始分割点索引（从此索引开始的消息属于"最近关键对话"）
     * @return 调整后的分割点索引
     */
    private int ensurePairingBoundary(List<Message> messages, int splitIdx) {
        if (splitIdx <= 0 || splitIdx >= messages.size()) {
            return splitIdx;
        }

        // 检查分割点左侧的 message：如果它是带 tool_calls 的 AssistantMessage，
        // 且分割点右侧第一个是 ToolResponseMessage，则需要将分割点左移
        Message left = messages.get(splitIdx - 1);
        Message right = messages.get(splitIdx);

        if (left instanceof AssistantMessage assistMsg
                && assistMsg.getToolCalls() != null
                && !assistMsg.getToolCalls().isEmpty()
                && right instanceof ToolResponseMessage) {
            log.debug("[Resilience] 调整分割点：{} → {}（保护 Assistant/Tool 配对）",
                    splitIdx, splitIdx - 1);
            return splitIdx - 1;
        }

        return splitIdx;
    }

    /**
     * 对旧历史消息生成摘要。
     *
     * <p>使用独立 LLM 请求，将历史消息压缩为精炼文本。
     *
     * @param client          ChatClient（用于摘要生成）
     * @param historyMessages 待摘要的历史消息
     * @param sessionId       会话 ID（用于日志）
     * @return 摘要文本，生成失败时返回 null
     */
    private String generateContextSummary(ChatClient client,
                                          List<Message> historyMessages,
                                          String sessionId) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return null;
        }

        // 将历史消息格式化为文本
        StringBuilder historyText = new StringBuilder();
        for (Message msg : historyMessages) {
            String role = msg.getMessageType().name();
            String text = msg.getText();
            if (text != null && !text.isBlank()) {
                // 截断每条消息最多 500 字符，控制摘要请求本身不超限
                String truncated = text.length() > 500
                        ? text.substring(0, 500) + "..."
                        : text;
                historyText.append("[").append(role).append("]: ").append(truncated).append("\n");
            }
        }

        if (historyText.isEmpty()) {
            return null;
        }

        String userPrompt = String.format(SUMMARIZE_ERR_USER_PROMPT, historyText);

        try {
            String result = client.prompt()
                    .system(SUMMARIZE_ERR_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .options(DefaultToolCallingChatOptions.builder()
                            .internalToolExecutionEnabled(false)
                            .temperature(0.3)
                            .build())
                    .call()
                    .content();

            if (result != null && !result.isBlank()) {
                // 限制摘要最长 3000 字符
                String trimmed = result.length() > 3000
                        ? result.substring(0, 3000) + "..."
                        : result;
                log.info("[Resilience] 上下文摘要生成成功: {} 条消息 → {} chars, session={}",
                        historyMessages.size(), trimmed.length(), sessionId);
                return trimmed;
            }
            return null;
        } catch (Exception ex) {
            log.error("[Resilience] 上下文摘要生成失败, session={}", sessionId, ex);
            // 降级：摘要生成失败时，只返回最近 2 条消息的原始文本作为摘要
            return fallbackSummaryFromMessages(historyMessages);
        }
    }

    /**
     * 摘要生成失败时的兜底：取最后 2 条消息的文本作为简单摘要。
     */
    private String fallbackSummaryFromMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        int count = Math.min(2, messages.size());
        StringBuilder sb = new StringBuilder("[摘要生成失败，以下为最近对话记录]\n");
        for (int i = messages.size() - count; i < messages.size(); i++) {
            Message msg = messages.get(i);
            String text = msg.getText();
            if (text != null && !text.isBlank()) {
                String shortText = text.length() > 300 ? text.substring(0, 300) + "..." : text;
                sb.append("[").append(msg.getMessageType().name()).append("]: ")
                        .append(shortText).append("\n");
            }
        }
        return sb.toString();
    }

    // ========================================================================
    // 内部枚举
    // ========================================================================

    /**
     * LLM 调用异常分类。
     */
    private enum ErrorType {
        /**
         * 线程中断 —— 不可恢复，需立即终止 Agent 执行
         */
        FATAL_INTERRUPTED,
        /**
         * 529 Server Overloaded —— 需备胎切换
         */
        OVERLOADED_529,
        /**
         * 429 Too Many Requests —— 需指数退避
         */
        RATE_LIMITED_429,
        /**
         * Context Length Exceeded —— 需摘要降级
         */
        CONTEXT_LENGTH_EXCEEDED,
        /**
         * 未识别的异常 —— 直接向上传播
         */
        UNKNOWN
    }
}
