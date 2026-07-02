package com.kaer.agent.messagebus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 消息总线中的单条消息记录。
 *
 * <p>作为 Agent 间异步通信的数据载体，每行 JSON 序列化后追加写入目标 Agent 的 .jsonl 收件箱文件。
 *
 * <p>字段约定：
 * <ul>
 *   <li>{@code requestId} — 请求唯一标识，用于请求-应答匹配</li>
 *   <li>{@code type} — 消息类型，建议取值：REQUEST / RESPONSE / STATUS_UPDATE</li>
 *   <li>{@code sender} — 发送者 Agent 名称</li>
 *   <li>{@code target} — 目标 Agent 名称</li>
 *   <li>{@code content} — 消息正文（自由文本或 JSON）</li>
 *   <li>{@code timestamp} — 消息创建时间</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecord {
    /** 请求唯一标识 */
    private String requestId;
    /** 消息类型：REQUEST / RESPONSE / STATUS_UPDATE */
    private Type type;
    /** 发送者名称 */
    private String sender;
    /** 目标 Agent 名称 */
    private String target;
    /** 消息正文 */
    private String content;
    /** 消息时间戳 */
    private Instant timestamp;
    public enum Type {
        /** Lead 向队友发指令时 type 用 REQUEST（默认） */
        REQUEST,
        /** 衔接PendingRequest的RESPONDED */
        RESPONSE,
        /** 队友主动汇报进度时 type 用 STATUS_UPDATE */
        STATUS_UPDATE
    }
}
