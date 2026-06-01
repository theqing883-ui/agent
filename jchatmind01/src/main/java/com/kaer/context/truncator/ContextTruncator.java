package com.kaer.context.truncator;

import com.kaer.context.token.TokenCounter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文截断策略：工具响应截断 + 按 token 预算的消息截断。
 */
@Component
public class ContextTruncator {

    private static final String TRUNCATION_SUFFIX = "...\n[已截断，完整结果已存储]";
    private final TokenCounter tokenCounter;

    public ContextTruncator(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    /**
     * 截断超出 token 上限的工具响应内容，防止单个工具响应占用过多上下文空间。
     * 
     * <p>处理逻辑：
     * 1. 遍历所有消息，仅处理 ToolResponseMessage 类型
     * 2. 对每个工具响应检查 token 数
     * 3. 超过上限时进行截断并添加截断标记
     * 4. 非工具响应消息保持不变
     * 
     * @param messages 消息列表
     * @param maxToolResponseTokens 单个工具响应的最大 token 数限制
     * @return 处理后的消息列表，工具响应已被适当截断
     */
    public List<Message> truncateToolResponses(List<Message> messages, int maxToolResponseTokens) {
        List<Message> result = new ArrayList<>();
        
        for (Message msg : messages) {
            // 仅处理工具响应消息
            if (msg instanceof ToolResponseMessage toolResp) {
                List<ToolResponseMessage.ToolResponse> capped = new ArrayList<>();
                
                // 遍历每个工具响应
                for (ToolResponseMessage.ToolResponse resp : toolResp.getResponses()) {
                    // 估算当前响应的 token 数
                    int tokens = tokenCounter.estimateTokens(resp.responseData());
                    
                    if (tokens > maxToolResponseTokens) {
                        // 超出上限，进行截断并添加截断标记
                        String truncated = truncateText(resp.responseData(), maxToolResponseTokens)
                                + TRUNCATION_SUFFIX;
                        capped.add(new ToolResponseMessage.ToolResponse(
                                resp.id(), resp.name(), truncated));
                    } else {
                        // 未超出上限，保持原样
                        capped.add(resp);
                    }
                }
                // 构建新的工具响应消息
                result.add(ToolResponseMessage.builder().responses(capped).build());
            } else {
                // 非工具响应消息直接保留
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 单条消息的工具响应截断（用于执行后实时截断）。
     */
    public Message truncateSingleToolResponse(Message message, int maxToolResponseTokens) {
        if (!(message instanceof ToolResponseMessage toolResp)) {
            return message;
        }
        List<ToolResponseMessage.ToolResponse> capped = new ArrayList<>();
        for (ToolResponseMessage.ToolResponse resp : toolResp.getResponses()) {
            int tokens = tokenCounter.estimateTokens(resp.responseData());
            if (tokens > maxToolResponseTokens) {
                String truncated = truncateText(resp.responseData(), maxToolResponseTokens)
                        + TRUNCATION_SUFFIX;
                capped.add(new ToolResponseMessage.ToolResponse(
                        resp.id(), resp.name(), truncated));
            } else {
                capped.add(resp);
            }
        }
        return ToolResponseMessage.builder().responses(capped).build();
    }

    /**
     * 按 token 数截断文本，采用比例估算方法截取前缀。
     * 
     * <p>算法原理：
     * 1. 计算文本的 token/字符比例（平均每个字符对应多少 token）
     * 2. 根据目标 token 数和比例反推需要保留的字符数
     * 3. 返回截取后的文本前缀
     * 
     * <p>设计考虑：
     * - 使用比例估算避免重复调用 tokenCounter，提高性能
     * - 设置最小比例 0.25 避免除零错误和极端情况
     * - 确保至少保留 1 个字符
     * 
     * @param text 待截断的文本
     * @param maxTokens 最大允许的 token 数
     * @return 截断后的文本前缀，不超过 maxTokens
     */
    private String truncateText(String text, int maxTokens) {
        // 空文本直接返回
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 计算 token/字符比例：平均每个字符对应多少 token
        double ratio = (double) tokenCounter.estimateTokens(text) / text.length();
        
        // 防止除零或极端比例，设置最小比例为 0.25
        if (ratio <= 0) ratio = 0.25;
        
        // 根据比例反推需要保留的字符数，至少保留 1 个字符
        int targetLen = Math.max(1, (int) (maxTokens / ratio));
        
        // 若目标长度大于等于原文本长度，直接返回原文本
        if (targetLen >= text.length()) {
            return text;
        }
        
        // 截取前缀并返回
        return text.substring(0, targetLen);
    }
}