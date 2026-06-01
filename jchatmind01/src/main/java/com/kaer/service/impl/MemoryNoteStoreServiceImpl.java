package com.kaer.service.impl;

import com.kaer.mapper.ChatMemoryNoteMapper;
import com.kaer.mapper.ChatMessageMapper;
import com.kaer.model.entity.ChatMemoryNote;
import com.kaer.service.MemoryNoteStoreService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久记忆笔记存储类，实现会话级缓存与数据库持久化的双层存储机制。
 *
 * <p>核心功能：
 * 1. 使用 ConcurrentHashMap 作为一级缓存，提供高效的读写性能
 * 2. 通过 ChatMemoryNoteMapper 实现数据库持久化，保证数据可靠性
 * 3. 支持按对话轮次间隔自动生成记忆笔记
 *
 * <p>设计特点：
 * - 线程安全：所有缓存操作均使用线程安全的 ConcurrentHashMap
 * - 缓存策略：首次访问时从数据库加载，后续直接从缓存读取
 * - 轮次计数：独立维护每个会话的对话轮次，用于控制笔记生成时机
 */
@AllArgsConstructor
@Component
public class MemoryNoteStoreServiceImpl implements MemoryNoteStoreService {

    /**
     * 数据库访问映射器，用于执行记忆笔记的持久化操作（CRUD）。
     * 通过构造函数注入，支持依赖注入和测试模拟。
     */
    private final ChatMemoryNoteMapper chatMemoryNoteMapper;
    /**
     * 根据会话ID查询所有对话记录。
     */
    private final ChatMessageMapper chatMessageMapper;
    /**
     * 会话轮次计数器，用于记录每个会话的对话轮次。
     * 使用 ConcurrentHashMap 保证线程安全，支持并发读写操作。
     */
    private final Map<String, Long> turnCounter = new ConcurrentHashMap<>();

    /**
     * 会话级记忆笔记缓存，键为会话ID，值为该会话的所有笔记内容列表。
     * 使用 ConcurrentHashMap 保证线程安全，支持并发读写操作。
     */
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();


    /**
     * 获取指定会话的所有记忆笔记。
     *
     * <p>采用缓存优先策略：
     * 1. 首先检查缓存中是否存在该会话的笔记
     * 2. 缓存命中则直接返回，避免数据库访问
     * 3. 缓存未命中则从数据库加载并填充缓存
     *
     * @param sessionId 会话唯一标识
     * @return 该会话的所有记忆笔记列表，若不存在则返回空列表
     */
    @Override
    public List<String> getNotes(String sessionId) {
        List<String> cached = cache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        // 首次加载从数据库读取
        List<ChatMemoryNote> dbNotes = chatMemoryNoteMapper.selectBySessionId(sessionId);
        List<String> notes = new ArrayList<>();
        if (dbNotes != null) {
            for (ChatMemoryNote note : dbNotes) {
                notes.add(note.getContent());
            }
        }
        cache.put(sessionId, notes);
        return notes;
    }

    /**
     * 添加一条记忆笔记，同时持久化到数据库并更新缓存。
     *
     * <p>操作流程：
     * 1. 构建 ChatMemoryNote 实体对象
     * 2. 通过 Mapper 插入数据库
     * 3. 使用 computeIfAbsent 确保缓存存在后添加新笔记
     *
     * @param sessionId 会话唯一标识
     * @param content   笔记内容
     */
    @Override
    public void addNote(String sessionId, String content) {
        ChatMemoryNote note = ChatMemoryNote.builder()
                .sessionId(sessionId)
                .content(content)
                .build();
        chatMemoryNoteMapper.insert(note);
        cache.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(content);
    }

    /**
     * 记录一轮对话，并判断是否应该生成记忆笔记。
     *
     * @param sessionId     会话唯一标识
     * @param intervalTurns 生成笔记的轮次间隔
     * @return 是否应该生成记忆笔记
     */
    @Override
    public boolean shouldGenerateNote(String sessionId, int intervalTurns) {
        // 根据会话ID查询所有对话记录。
//        long turns = chatMessageMapper.countBySessionId(sessionId);
        long turns = turnCounter.merge(sessionId, 1L, Long::sum);
        // 当轮次是间隔的整数倍时返回true（如第8、16、24轮...）
        return turns % intervalTurns == 0;
    }
}