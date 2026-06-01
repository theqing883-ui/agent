package com.kaer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaer.message.SseMessage;
import com.kaer.service.SseService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SSE（Server-Sent Events）服务实现类
 * 提供服务端向客户端推送消息的能力，基于 Spring SseEmitter 实现
 */
@Service
@AllArgsConstructor
public class SseServiceImpl implements SseService {

    /**
     * 客户端连接映射表
     * key: chatSessionId（会话ID）
     * value: SseEmitter（SSE发射器，用于向客户端发送消息）
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final ConcurrentMap<String, SseEmitter> clients = new ConcurrentHashMap<>();

    /**
     * JSON序列化工具，用于将消息对象转换为JSON字符串
     */
    private final ObjectMapper objectMapper;

    /**
     * 建立SSE连接
     * 创建SseEmitter并注册到客户端映射表，设置超时时间为30分钟
     *
     * @param chatSessionId 会话ID，用于标识客户端连接
     * @return SseEmitter 对象，供Spring MVC框架处理SSE响应
     * @throws RuntimeException 当发送初始化消息失败时抛出
     */
    @Override
    public SseEmitter connect(String chatSessionId) {
        // 创建SSE发射器，设置超时时间为30分钟（30 * 60 * 1000毫秒）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        // 将发射器注册到客户端映射表
        clients.put(chatSessionId, emitter);

        try {
            // 发送初始化消息，通知客户端连接已建立
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data("connected")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 注册连接完成回调：从映射表中移除客户端
        emitter.onCompletion(() -> {
            clients.remove(chatSessionId);
        });
        // 注册超时回调：从映射表中移除客户端
        emitter.onTimeout(() -> clients.remove(chatSessionId));
        // 注册错误回调：从映射表中移除客户端
        emitter.onError((error) -> clients.remove(chatSessionId));

        return emitter;
    }

    /**
     * 向指定客户端发送消息
     *
     * @param chatSessionId 会话ID，标识目标客户端
     * @param message       待发送的消息对象
     * @throws RuntimeException 当客户端不存在或消息发送失败时抛出
     */
    @Override
    public void send(String chatSessionId, SseMessage message) {
        // 根据会话ID获取对应的SseEmitter
        SseEmitter emitter = clients.get(chatSessionId);

        if (emitter != null) {
            try {
                // 将消息对象序列化为JSON字符串
                String sseMessageStr = objectMapper.writeValueAsString(message);
                // 发送SSE事件
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(sseMessageStr)
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("No client found for chatSessionId: " + chatSessionId);
        }
    }
}