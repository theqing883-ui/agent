package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 优雅停止工具——后台队友调用此工具标记自己在完成本轮工作后永久停止。
 *
 * <p>FIXED 类型。TeammateConfig.excludedTools 不应排除此工具（队友需要它）。
 *
 * <p>调用流程：
 * <ol>
 *   <li>队友在 think 阶段收到 Lead 的 REQUEST（或自行决定退出）</li>
 *   <li>队友调用 {@code shutdown()} 设置 NEEDS_SHUTDOWN 标志</li>
 *   <li>队友调用 {@code replyToLead} 向 Lead 确认（如适用）</li>
 *   <li>队友调用 {@code terminate} 结束当前 think-execute 循环</li>
 *   <li>TeammateWorker 在 agent.run() 返回后检测到 NEEDS_SHUTDOWN 标志，
 *       将 shouldRestart 置为 false，循环退出，线程结束</li>
 * </ol>
 */
@Slf4j
@Component
public class ShutdownTool implements Tool {

    @Override
    public String getName() {
        return "shutdown";
    }

    @Override
    public String getDescription() {
        return "标记当前队友在完成本轮工作后永久停止，不再被重启。调用后应接着调用 terminate。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 标记本轮结束后不重启。
     *
     * <p>调用此工具后，TeammateWorker 在本次 agent.run() 返回后将不再重启。
     * 通常在收到 Lead 的 REQUEST 后、回复 Lead 之前调用。
     *
     * @return 确认消息
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "shutdown",
            description = """
                    标记当前队友在本轮工作完成后永久停止。TeammateWorker 将不再重启。
                    调用此工具后请接着调用 terminate 退出当前循环。
                    通常在收到 Lead 的 REQUEST 要求关机时使用。"""
    )
    public String shutdown() {
        AgentContextHolder.markNeedsShutdown();
        log.info("Teammate 已标记 NEEDS_SHUTDOWN——本轮结束后不再重启");
        return "已标记在完成本轮工作后停止。请回复 Lead（如有 REQUEST 待回复），"
                + "然后调用 terminate 退出。";
    }
}
