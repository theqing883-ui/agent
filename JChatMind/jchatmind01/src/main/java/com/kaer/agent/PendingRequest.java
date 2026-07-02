package com.kaer.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Lead 发出的一条尚未收到回复的异步请求。
 *
 * <p>当 Lead 通过 sendMessage 向 Teammate 发送 REQUEST 类型消息时，
 * 工具层自动生成 requestId 并注册一条 PendingRequest。
 * 当 Teammate 通过 replyToLead 回复（携带同一 requestId）后，
 * JChatMind.pollInbox() 自动匹配并更新状态为 RESPONDED。
 *
 * <p>requestId 是贯穿 Req→ACK 全链路的关联键（Correlation ID）。
 */
@Data
@AllArgsConstructor
public class PendingRequest {

    /** 关联键，贯穿请求→响应全链路 */
    private String requestId;

    /** 请求发往的目标 Agent 名称 */
    private String target;

    /** 请求内容摘要 */
    private String content;

    /** 请求发送时间 */
    private Instant sentAt;

    /** 当前状态 */
    private Status status;

    /** 收到的回复内容（RESPONDED 时填充） */
    private String responseContent;

    /** 回复到达时间（RESPONDED 时填充） */
    private Instant respondedAt;

    public enum Status {
        /** 已发出，等待回复 */
        AWAITING,
        /** 已收到回复 */
        RESPONDED,
        /** 超时未回复 */
        TIMEOUT
    }
}
