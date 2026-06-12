package com.kaer.controller;

import com.kaer.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag/evaluate")
public class RagTestController {

    private final RagService ragService;
    private final ChatClient chatClient;

    public RagTestController(RagService ragService, ChatClient chatClient) {
        this.ragService = ragService;
        this.chatClient = chatClient;
    }

    /**
     * 专门给 Python 评估脚本调用的接口
     */
    @PostMapping("/run")
    public Map<String, Object> runTest(@RequestBody Map<String, String> request) {
        String kbId = request.get("kbId");
        String question = request.get("question");
        String keyword = request.get("keyword"); // 如果你是在脚本里提前提取好的

        // 1. 调用你写好的混合检索服务，拿到上下文片段
        List<String> contexts = ragService.similaritySearch(kbId, question, keyword);
        String contextStr = String.join("\n\n", contexts);

        // 2. 组装 Prompt 让大模型生成回答
        String prompt = String.format("请基于以下知识片段回答问题。\n片段：\n%s\n\n问题：%s", contextStr, question);
        String answer = chatClient.prompt().user(prompt).call().content();

        // 3. 将 Ragas 必须的字段打包返回
        Map<String, Object> result = new HashMap<>();
        result.put("question", question);
        result.put("contexts", contexts); // 注意这里返回的是 List<String>
        result.put("answer", answer);

        return result;
    }
}