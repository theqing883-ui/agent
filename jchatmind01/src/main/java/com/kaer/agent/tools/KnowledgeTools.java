package com.kaer.agent.tools;

import com.kaer.service.RagService;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeTools implements Tool {

    private final RagService ragService;

    public KnowledgeTools(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String getName() {
        return "KnowledgeTool";
    }

    @Override
    public String getDescription() {
        return "用于从知识库执行语义检索（RAG）。输入知识库 ID 和查询文本，返回与查询最相关的内容片段。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "KnowledgeTool",
            description = "核心知识库检索工具（混合检索 RAG）。当用户提问需要参考系统内的文档、规章、项目信息或专业资料时调用。该工具结合了语义理解与关键词匹配，返回最相关的上下文片段。"
    )
    public String knowledgeQuery(
            @ToolParam(description = "目标知识库 ID。注意：如果用户提问时未明确指定知识库，或者你当前上下文中没有可用的知识库 ID，请【务必先】调用 databaseQuery 工具查询 knowledge_base 表，根据描述获取正确的 ID 后，再调用本工具。") String kbsId,

            @ToolParam(description = "完整的自然语言提问，用于语义向量检索。请保留用户的核心意图、疑问词和完整语境（例如：'在Spring Boot项目中，通常是如何处理高并发场景的？'）。") String query,

            @ToolParam(description = "从提问中提取的核心关键词，用于精确的全文匹配。请提取最具代表性的专业名词或动词短语，剔除如'怎么'、'的'等无意义停用词。多个关键词之间【必须用一个半角空格隔开】（例如：'Spring Boot 高并发 并发处理'）。") String keyword) {

        // 将两个不同的 query 传给底层的检索服务
        List<String> strings = ragService.similaritySearch(kbsId, query, keyword);
        return String.join("\n", strings);
    }
}
