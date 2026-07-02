package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import com.kaer.agent.PendingRequest;
import com.kaer.agent.messagebus.MessageBus;
import com.kaer.agent.messagebus.MessageRecord;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 发送消息工具——通过 MessageBus 向其他 Agent 发送异步消息。
 *
 * <p>所有 Agent 默认可用（FIXED 类型）。
 * Lead 用此工具向 Teammate 发送指令（REQUEST），Teammate 用 {@link ReplyToLeadTool} 回复。
 *
 * <h3>Correlation ID 机制</h3>
 * <p>{@code requestId} 由工具层自动生成（UUID 前 8 位），LLM 无需关心。
 * 发送 REQUEST 时自动注册 {@link PendingRequest} 到 JChatMind，
 * 后续 pollInbox 收到 RESPONSE 时自动按 requestId 匹配。
 */
@Slf4j
@Component
@AllArgsConstructor
public class SendMessageTool implements Tool {

    private final MessageBus messageBus;

    @Override
    public String getName() {
        return "sendMessage";
    }

    @Override
    public String getDescription() {
        return "向其他 Agent 发送异步消息。Lead 用 REQUEST 给队友发指令，队友用 STATUS_UPDATE 主动汇报进度。回复 Lead 的请求请使用 replyToLead 工具。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 向目标 Agent 发送消息。
     *
     * @param target  目标 Agent 名称（如 "alice", "researcher"）
     * @param content 消息内容
     * @param type    消息类型：REQUEST（发指令）/ STATUS_UPDATE（主动汇报），默认 REQUEST
     * @return 发送结果（包含自动生成的 requestId，用于追踪回复）
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "sendMessage",
            description = """
                    向目标 Agent 发送异步消息。
                    - Lead 向队友发指令时 type 用 REQUEST（默认）
                    - 队友主动汇报进度时 type 用 STATUS_UPDATE
                    - 回复 Lead 的 REQUEST 请使用 replyToLead 工具
                    requestId 由系统自动生成（不需要你提供），返回结果中会包含它。"""
    )
    public String sendMessage(
            @ToolParam(description = "目标 Agent 名称（如 'alice', 'researcher'）") String target,
            @ToolParam(description = "消息内容") String content,
            @ToolParam(description = "消息类型：REQUEST / STATUS_UPDATE（默认 REQUEST）", required = false) String type) {

        // 参数校验
        if (target == null || target.isBlank()) {
            return "[发送失败] 目标 Agent 名称不能为空。";
        }
        if (content == null || content.isBlank()) {
            return "[发送失败] 消息内容不能为空。";
        }

        // requestId 由工具层自动生成，LLM 不感知
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String effectiveType = (type != null && !type.isBlank()) ? type : MessageRecord.Type.REQUEST.toString();

        // 获取发送者名称
        String sender = AgentContextHolder.getAgentId();
        if (sender == null || sender.isBlank()) {
            sender = "unknown";
        }

        MessageRecord record = MessageRecord.builder()
                .requestId(requestId)
                .type(MessageRecord.Type.valueOf(effectiveType))
                .sender(sender)
                .target(target)
                .content(content)
                .timestamp(Instant.now())
                .build();

        try {
            messageBus.send(target, record);
            log.info("消息已发送: sender={}, target={}, type={}, requestId={}",
                    sender, target, effectiveType, requestId);

            // 发送 REQUEST 时注册 PendingRequest，用于后续 pollInbox 匹配 RESPONSE
            if (MessageRecord.Type.REQUEST.toString().equals(effectiveType)) {
                Consumer<PendingRequest> callback = AgentContextHolder.getPendingRequestCallback();
                if (callback != null) {
                    callback.accept(new PendingRequest(
                            requestId, target, content, Instant.now(),
                            PendingRequest.Status.AWAITING, null, null));
                    log.debug("PendingRequest 已注册: requestId={}, target={}", requestId, target);
                }
            }

            return String.format("消息已发送至 %s（类型: %s, requestId: %s）", target, effectiveType, requestId);
        } catch (Exception e) {
            log.error("发送消息失败: target={}, error={}", target, e.getMessage(), e);
            return "[发送失败] 无法发送消息到 " + target + ": " + e.getMessage();
        }
    }
}
