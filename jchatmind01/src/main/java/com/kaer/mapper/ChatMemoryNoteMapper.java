package com.kaer.mapper;

import com.kaer.model.entity.ChatMemoryNote;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMemoryNoteMapper {
    List<ChatMemoryNote> selectBySessionId(String sessionId);

    int insert(ChatMemoryNote note);

//    int deleteBySessionId(String sessionId);
}
