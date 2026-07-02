package com.kaer.context.compact;

import com.kaer.context.token.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * L1 — 头尾保留滑动窗口修剪（规则裁剪，不调用 LLM）。
 *
 * <h3>算法 (Slice & Contract)</h3>
 * <p>当非系统消息总数超过 (keepFirstK + keepLastN) 时，直接截取开头 K 条和末尾 N 条，
 * 丢弃中间段。
 *
 * <h3>边界修正 (孤儿消除)</h3>
 * <ol>
 * <li><b>Orphan Caller</b>：如果 Head 的最后一条是发起工具调用的 AssistantMessage，
 * 但它的回执在中间段被丢弃了，则从 Head 移除该 Caller。</li>
 * <li><b>Orphan Response</b>：如果 Tail 的第一条是工具的回执 ToolResponseMessage，
 * 但它的 Caller 在中间段被丢弃了，则从 Tail 移除该 Response。</li>
 * </ol>
 */
@Slf4j
@Component
public class L1SlidingWindowTrim implements ContextCompactor {

    private final TokenCounter tokenCounter;

    public L1SlidingWindowTrim(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    @Override
    public CompactResult compact(List<Message> messages, CompactorContext context) {
        int keepFirstK = context.getKeepFirstK();
        int keepLastN = context.getKeepLastN();

        if (messages.isEmpty() || (keepFirstK <= 0 && keepLastN <= 0)) {
            return CompactResult.continue_(messages, "L1");
        }

        // 步1: 分离 SystemMessage 和非 SystemMessage (系统消息绝对免疫裁剪)
        List<Message> systemMsgs = new ArrayList<>();
        List<Message> nonSystemMsgs = new ArrayList<>();
        for (Message msg : messages) {
            if (msg instanceof SystemMessage) {
                systemMsgs.add(msg);
            } else {
                nonSystemMsgs.add(msg);
            }
        }

        int total = nonSystemMsgs.size();

        // 步2: 总条数未超阈值 → 无需修剪
        if (total <= keepFirstK + keepLastN) {
            log.debug("[L1] 非系统消息数({})未超过阈值({}+{})，跳过", total, keepFirstK, keepLastN);
            return CompactResult.continue_(messages, "L1");
        }

        int beforeTokens = tokenCounter.estimateTokens(messages);

        // 步3: 暴力切片 (直接截取合法的 Head 和 Tail 区间)
        // 因为 total > K + N，所以这两个区间绝对不会重叠，中间必然存在被丢弃的区域
        List<Message> headMsgs = new ArrayList<>(nonSystemMsgs.subList(0, keepFirstK));
        List<Message> tailMsgs = new ArrayList<>(nonSystemMsgs.subList(total - keepLastN, total));

        // 步4: 边界修正 — 检查 Head 切口处是否制造了 Orphan Caller
        if (!headMsgs.isEmpty()) {
            Message lastHead = headMsgs.get(headMsgs.size() - 1);
            if (lastHead instanceof AssistantMessage assist && assist.hasToolCalls()) {
                // 查看原列表中紧挨着 Head 之后的那个消息，如果是 ToolResponse，说明这对被切散了
                if (nonSystemMsgs.get(keepFirstK) instanceof ToolResponseMessage) {
                    headMsgs.remove(headMsgs.size() - 1);
                    log.debug("[L1] 边界修正: 移除 Head 末尾失去回执的孤儿 AssistantMessage");
                }
            }
        }

        // 步5: 边界修正 — 检查 Tail 切口处是否制造了 Orphan Response
        if (!tailMsgs.isEmpty()) {
            Message firstTail = tailMsgs.get(0);
            if (firstTail instanceof ToolResponseMessage) {
                // 查看原列表中紧挨着 Tail 之前的那个消息，如果是 Assistant(tc)，说明这对被切散了
                int tailStartIdx = total - keepLastN;
                if (nonSystemMsgs.get(tailStartIdx - 1) instanceof AssistantMessage assist && assist.hasToolCalls()) {
                    tailMsgs.remove(0);
                    log.debug("[L1] 边界修正: 移除 Tail 开头失去调用者的孤儿 ToolResponseMessage");
                }
            }
        }

        // 步6: 组装结果
        List<Message> result = new ArrayList<>(systemMsgs);
        result.addAll(headMsgs);
        result.addAll(tailMsgs);

        int afterTokens = tokenCounter.estimateTokens(result);
        boolean satisfied = afterTokens <= context.getTokenBudget();
        int droppedCount = total - headMsgs.size() - tailMsgs.size();

        log.info("[L1] 滑动窗口: 非系统消息 {}→{} (头{}/尾{}), 丢弃 {} 条中间消息, "
                        + "tokens {}→{}, budget={}, satisfied={}",
                total, headMsgs.size() + tailMsgs.size(),
                headMsgs.size(), tailMsgs.size(), droppedCount,
                beforeTokens, afterTokens, context.getTokenBudget(), satisfied);

        if (satisfied) {
            return CompactResult.pass(result, "L1");
        }
        return CompactResult.continue_(result, "L1");
    }
}