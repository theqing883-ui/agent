package com.kaer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MessageBus 配置属性。
 *
 * <p>对应 {@code application.yml} 中 {@code agent.messagebus} 前缀的配置节。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.messagebus")
public class MessageBusConfig {

    /**
     * 收件箱文件目录。
     *
     * <p>每个 Agent 在该目录下拥有一个独立的 {@code <agentName>.jsonl} 文件。
     * 默认为 {@code ./jchatmind01/data/inboxes}。
     */
    private String inboxDir = "./JChatMind/jchatmind01/data/inboxes";
}
