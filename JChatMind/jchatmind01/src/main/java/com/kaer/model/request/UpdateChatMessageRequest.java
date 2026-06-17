package com.kaer.model.request;

import com.kaer.model.dto.ChatMessageDTO;
import lombok.Data;

@Data
public class UpdateChatMessageRequest {
    private String content;
    private ChatMessageDTO.MetaData metadata;
}

