package com.kaer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 队友 (Teammate) 配置属性。
 *
 * <p>对应 {@code application.yml} 中 {@code agent.teammate} 前缀的配置节。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.teammate")
public class TeammateConfig {

    /**
     * JChatMind.run() 退出后重启前的等待间隔（毫秒，默认 2000）。
     *
     * <p>当队友因 terminate（暂无可执行任务）或异常退出时，
     * 等待此间隔后重新启动 agent.run()，让队友持续工作。
     */
    private long restartDelayMs = 2000;

    /**
     * 队友创建时排除的工具名称列表。
     *
     * <p>防止队友递归委派（delegateTask）或无限唤醒子队友（spawnTeammate）。
     */
    private List<String> excludedTools = List.of("delegateTask", "spawnTeammate", "stopTeammate", "createTask");

    /**
     * 单个父 Agent（会话）最多允许的队友数量（默认 3）。
     *
     * <p>超过此数量后 spawnTeammate 会拒绝创建新队友，
     * 防止 Lead 无限扩编导致系统资源耗尽。
     */
    private int maxTeammates = 3;
}
