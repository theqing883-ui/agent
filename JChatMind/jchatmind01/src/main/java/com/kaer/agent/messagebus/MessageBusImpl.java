package com.kaer.agent.messagebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaer.config.MessageBusConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.StandardOpenOption.*;

/**
 * 基于文件的 MessageBus 实现。
 *
 * <h3>并发安全设计</h3>
 * <p>使用 {@link ConcurrentHashMap}&lt;String, ReentrantLock&gt; 为每个 Agent 的收件箱文件维护独立锁。
 * 同一 Agent 文件的读写操作互斥，不同 Agent 之间完全并行，无竞争。
 *
 * <h3>写入</h3>
 * <p>使用 {@link Files#write(Path, byte[], java.nio.file.OpenOption...)} 配合 {@code CREATE} + {@code APPEND}
 * 追加写入。在锁保护下保证 JVM 内无交错写入。
 *
 * <h3>消费式读取</h3>
 * <p>读取全部行后立即通过 {@code TRUNCATE_EXISTING} 清空文件，
 * 保证每条消息在其生命周期内只被消费一次。
 * 损坏的 JSON 行会被跳过并记录警告日志，不会导致整个读取失败。
 */
@Slf4j
@Component
public class MessageBusImpl implements MessageBus {

    private final MessageBusConfig config;
    private final ObjectMapper objectMapper;

    /**
     * 每个 Agent 名称对应一个独立的 ReentrantLock，
     * 保证同一文件的读写操作串行化。
     */
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    public MessageBusImpl(MessageBusConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    // ==================== MessageBus 接口实现 ====================

    @Override
    public void send(String targetAgentName, MessageRecord message) {
        ReentrantLock lock = getLock(targetAgentName);
        lock.lock();
        try {
            Path path = resolveInboxFile(targetAgentName);
            ensureDirectoryExists(path);
            String line = objectMapper.writeValueAsString(message) + "\n";
            Files.writeString(path, line, CREATE, APPEND);
            log.debug("消息已发送: target={}, sender={}, type={}, id={}",
                    targetAgentName, message.getSender(), message.getType(), message.getRequestId());
        } catch (IOException e) {
            log.error("发送消息失败: target={}, error={}", targetAgentName, e.getMessage(), e);
            throw new RuntimeException("发送消息到 " + targetAgentName + " 失败", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<MessageRecord> readInbox(String agentName) {
        ReentrantLock lock = getLock(agentName);
        lock.lock();
        try {
            Path path = resolveInboxFile(agentName);
            if (!Files.exists(path)) {
                return List.of();
            }

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            // 消费式读取：读完后立即清空文件
            try (var os = Files.newOutputStream(path, TRUNCATE_EXISTING)) {
                // 仅清空，不写入
            }

            if (lines.isEmpty()) {
                return List.of();
            }

            List<MessageRecord> records = lines.stream()
                    .filter(line -> !line.isBlank())
                    .map(this::parseLine)
                    .filter(Objects::nonNull)
                    .toList();

            log.debug("收件箱已读取并清空: agent={}, 消息数={}", agentName, records.size());
            return records;

        } catch (IOException e) {
            log.error("读取收件箱失败: agent={}, error={}", agentName, e.getMessage(), e);
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 解析单行 JSON 为 MessageRecord。
     *
     * @param line JSON 行
     * @return 解析成功返回 MessageRecord，失败返回 null
     */
    private MessageRecord parseLine(String line) {
        try {
            return objectMapper.readValue(line, MessageRecord.class);
        } catch (IOException e) {
            log.warn("跳过损坏的 JSON 行，内容前 80 字符: {}...", line.substring(0, Math.min(80, line.length())));
            return null;
        }
    }

    /**
     * 将 Agent 名称解析为收件箱文件路径。
     *
     * <p>Agent 名称会被转为小写并去除非法文件名字符，防止路径遍历攻击。
     */
    private Path resolveInboxFile(String agentName) {
        // 转为小写，只保留字母数字、连字符、下划线
        String safe = agentName.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return Path.of(config.getInboxDir(), safe + ".jsonl");
    }

    /**
     * 确保收件箱文件的父目录存在。
     */
    private void ensureDirectoryExists(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * 获取或创建 Agent 对应的文件锁。
     *
     * <p>使用 {@link ConcurrentHashMap#computeIfAbsent} 保证同一 Agent 名称只创建一个锁。
     */
    private ReentrantLock getLock(String agentName) {
        return fileLocks.computeIfAbsent(agentName, k -> new ReentrantLock());
    }
}
