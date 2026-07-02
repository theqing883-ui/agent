package com.kaer.context.compact;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 上下文压缩器接口 —— 对消息列表执行一级压缩操作。
 *
 * <p>实现类应是无状态的（或仅依赖注入的 Spring Bean），
 * 由 {@link CompactorPipeline} 按顺序调用。
 */
@FunctionalInterface
public interface ContextCompactor {

    /**
     * 执行压缩。
     *
     * @param messages 输入消息列表（通常是上一级的输出）
     * @param context  压缩上下文（配置 + 服务依赖）
     * @return 压缩后的消息列表及元信息
     */
    CompactResult compact(List<Message> messages, CompactorContext context);
}
