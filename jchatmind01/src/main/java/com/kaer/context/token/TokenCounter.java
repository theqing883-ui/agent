package com.kaer.context.token;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Token 估算器，基于字符数做中英双语 token 估算。
 * 提供 Message 级别、列表级别和纯文本级别的 token 计数。
 */
@Component
public class TokenCounter {

    private static final double CJK_CHARS_PER_TOKEN = 1.5;
    private static final double LATIN_CHARS_PER_TOKEN = 4.0;

    private static final Pattern CJK_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff\\u3000-\\u303f\\uff00-\\uffef]+"
    );

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkChars = 0;
        int latinChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || (ch >= 0x3000 && ch <= 0x303f)
                    || (ch >= 0xff00 && ch <= 0xffef)
                    || (ch >= 0x3400 && ch <= 0x4dbf)) {
                cjkChars++;
            } else if (!Character.isWhitespace(ch)) {
                latinChars++;
            }
        }
        return (int) Math.ceil(cjkChars / CJK_CHARS_PER_TOKEN + latinChars / LATIN_CHARS_PER_TOKEN);
    }

    public int estimateTokens(Message message) {
        int tokens = 0;
        String text = message.getText();
        if (text != null) {
            tokens += estimateTokens(text);
        }
        if (message instanceof AssistantMessage assistMsg && assistMsg.getToolCalls() != null) {
            for (AssistantMessage.ToolCall tc : assistMsg.getToolCalls()) {
                tokens += estimateTokens(tc.name());
                tokens += estimateTokens(tc.arguments());
            }
        }
        if (message instanceof ToolResponseMessage toolResp) {
            for (ToolResponseMessage.ToolResponse resp : toolResp.getResponses()) {
                tokens += estimateTokens(resp.name());
                tokens += estimateTokens(resp.responseData());
            }
        }
        return tokens;
    }

    public int estimateTokens(List<Message> messages) {
        return messages.stream().mapToInt(this::estimateTokens).sum();
    }
}
