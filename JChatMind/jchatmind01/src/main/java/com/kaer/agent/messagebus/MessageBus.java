package com.kaer.agent.messagebus;

import java.util.List;

/**
 * 消息总线接口——Agent 间异步通信的核心抽象。
 *
 * <p>每个 Agent 拥有一个独立的 .jsonl 文件作为收件箱。
 * <ul>
 *   <li>{@link #send(String, MessageRecord)} — 追加写入目标 Agent 的收件箱文件</li>
 *   <li>{@link #readInbox(String)} — 消费式读取收件箱（读后立即清空），保证每条消息只被消费一次</li>
 * </ul>
 */
public interface MessageBus {

    /**
     * 向目标 Agent 发送消息。
     *
     * @param targetAgentName 目标 Agent 的名称
     * @param message         消息记录
     */
    void send(String targetAgentName, MessageRecord message);

    /**
     * 读取并清空指定 Agent 的收件箱。
     *
     * <p>这是消费式读取：读取成功后文件内容被清空，
     * 保证每条消息在其生命周期内只被消费一次。
     *
     * @param agentName Agent 名称
     * @return 收件箱中的所有消息（按写入顺序排列）；若文件不存在则返回空列表
     */
    List<MessageRecord> readInbox(String agentName);
}
