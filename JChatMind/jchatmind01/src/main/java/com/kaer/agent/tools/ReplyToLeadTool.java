package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import com.kaer.agent.messagebus.MessageBus;
import com.kaer.agent.messagebus.MessageRecord;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 回复 Lead 工具——队友专用，向 Lead Agent 回复某个请求的执行结果。
 *
 * <p>FIXED 类型，所有 Agent 默认可用。
 *
 * <h3>与 sendMessage 的区别</h3>
 * <ul>
 *   <li>{@code sendMessage} — Lead 向队友发指令，或队友做主动进度汇报（STATUS_UPDATE）</li>
 *   <li>{@code replyToLead} — 队友回复 Lead 的具体请求（RESPONSE），
 *       必须携带收到的 {@code requestId} 以关联原始请求</li>
 * </ul>
 *
 * <h3>Correlation ID</h3>
 * <p>{@code requestId} 必须与 Lead 发来的 REQUEST 消息中的 requestId 一致。
 * Lead 侧的 pollInbox 收到此 RESPONSE 后，会自动按 requestId 匹配 PendingRequest 并更新状态。
 */
@Slf4j
@Component
@AllArgsConstructor
public class ReplyToLeadTool implements Tool {

    private final MessageBus messageBus;

    @Override
    public String getName() {
        return "replyToLead";
    }

    @Override
    public String getDescription() {
        return "回复 Lead Agent 的某个请求。使用收到的 requestId 做关联，确保 Lead 能追踪到回复。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 向 Lead 回复某个请求的结果。
     *
     * @param content   回复内容（执行结果、确认信息等）
     * @param requestId 要回复的请求 ID（必须与收到的 REQUEST 消息中的 requestId 一致）
     * @return 发送结果
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "replyToLead",
            description = """
                    向 Lead Agent 回复某个 REQUEST 的执行结果。
                    requestId 必须与收到的 REQUEST 消息中标注的 requestId 完全一致，
                    这样 Lead 才能将回复与原始请求匹配。"""
    )
    public String replyToLead(
            @ToolParam(description = "回复内容（执行结果、确认信息等）") String content,
            @ToolParam(description = "要回复的请求 ID（必须与收到的 REQUEST 中的 requestId 一致）") String requestId) {

        // 参数校验
        if (content == null || content.isBlank()) {
            return "[回复失败] 回复内容不能为空。";
        }
        if (requestId == null || requestId.isBlank()) {
            return "[回复失败] requestId 不能为空。请使用收到的 REQUEST 消息中标注的 requestId。";
        }

        // Lead 名称由 TeammateWorker 在启动时设置到 AgentContextHolder
        String leadName = AgentContextHolder.getLeadName();
        if (leadName == null || leadName.isBlank()) {
            return "[回复失败] 未找到 Lead Agent。replyToLead 仅支持在 Teammate 上下文中使用。"
                    + "如果你是 Lead，请使用 sendMessage。";
        }

        String sender = AgentContextHolder.getAgentId();
        if (sender == null || sender.isBlank()) {
            sender = "unknown";
        }

        MessageRecord record = MessageRecord.builder()
                .requestId(requestId)        // 关键：使用相同的 requestId 做关联
                .type(MessageRecord.Type.RESPONSE)
                .sender(sender)
                .target(leadName)
                .content(content)
                .timestamp(Instant.now())
                .build();

        try {
            messageBus.send(leadName, record);
            log.info("Teammate 已回复 Lead: sender={}, lead={}, requestId={}",
                    sender, leadName, requestId);
            return "已向 Lead 回复（requestId: " + requestId + "）";
        } catch (Exception e) {
            log.error("回复 Lead 失败: lead={}, requestId={}, error={}",
                    leadName, requestId, e.getMessage(), e);
            return "[回复失败] 无法向 Lead 发送回复: " + e.getMessage();
        }
    }
}
