package com.kaer.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多 Agent 任务委派全局配置。
 * <p>
 * 可通过 application.yml 的 {@code agent.delegation} 前缀覆盖默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.delegation")
public class DelegationConfig {

    /** 子 Agent 最大 think-execute 循环步数，默认 10 */
    private int childMaxSteps = 10;

    /** 子 Agent 中排除的工具名称列表，默认排除 "delegateTask" 防止无限递归 */
    private List<String> excludedTools = List.of("delegateTask");

    /** 子 Agent 结果最大字符数，超过则截断，默认 4000 */
    private int maxResultLength = 4000;
}
