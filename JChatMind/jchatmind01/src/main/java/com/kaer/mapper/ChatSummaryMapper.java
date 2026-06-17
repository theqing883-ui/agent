package com.kaer.mapper;

import com.kaer.model.entity.ChatSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSummaryMapper {

    ChatSummary selectBySessionId(String sessionId);

    int upsertSummary(ChatSummary chatSummary);
}