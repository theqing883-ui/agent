package com.kaer.service.impl;

import com.kaer.mapper.ChunkBgeM3Mapper;
import com.kaer.model.entity.ChunkBgeM3;
import com.kaer.service.RagService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * RAG（Retrieval-Augmented Generation）服务实现类
 * 提供文本向量化和相似度搜索功能，使用 Ollama 的 bge-m3 模型
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {

    private final RedisVectorService redisVectorService;

    /**
     * WebClient 实例，用于调用 Ollama 向量化服务
     * 配置为连接 http://localhost:11434
     */
    private final WebClient webClient;

    /**
     * ChunkBgeM3 Mapper，用于执行向量相似度搜索查询
     */
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;

    /**
     * 构造函数，初始化 WebClient 和 Mapper
     *
     * @param builder           WebClient 构建器
     * @param chunkBgeM3Mapper 向量数据访问对象
     */
    public RagServiceImpl(WebClient.Builder builder, ChunkBgeM3Mapper chunkBgeM3Mapper,
                          RedisVectorService redisVectorService) {
        // 配置 WebClient，连接 Ollama 服务
        this.webClient = builder
                .baseUrl("http://localhost:11434")
                .build();
        this.chunkBgeM3Mapper = chunkBgeM3Mapper;
        this.redisVectorService = redisVectorService;
    }

    /**
     * 将文本转换为向量表示
     *
     * @param text 待向量化的文本
     * @return 文本的向量表示（float数组）
     */
    @Override
    public float[] embed(String text) {
        return doEmbed(text);
    }

    /**
     * 调用 Ollama 的 bge-m3 模型进行文本向量化
     *
     * @param text 待向量化的文本
     * @return 文本的向量表示（float数组）
     * @throws IllegalArgumentException 当向量化响应为空时抛出
     */
    public float[] doEmbed(String text) {
        // 调用 Ollama 的 embedding API
        EmbeddingResponse response = webClient.post()
                .uri("api/embeddings")
                .bodyValue(Map.of(
                        "model", "bge-m3",  // 使用 bge-m3 向量化模型
                        "prompt", text       // 待向量化的文本
                ))
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .block();  // 同步阻塞获取响应

        // 校验响应不为空
        Assert.notNull(response, "Embedding response cannot be null");
        return response.getEmbedding();
    }

    /**
     * 在指定知识库中执行相似度搜索
     *
     * @param kbId  知识库ID
     * @param title 查询文本（用于生成查询向量）
     * @return 相似度最高的前3个文本片段内容列表
     */
    @Override
    public List<String> similaritySearch(String kbId, String title) {
        float[] queryVector = doEmbed(title);

        List<String> chunkIds = redisVectorService.search(kbId, queryVector, 3);
        log.info("搜索到的 chunkIds: {}", chunkIds);
        if (chunkIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkBgeM3> chunks = chunkBgeM3Mapper.selectByIds(chunkIds);
        return chunks.stream().map(ChunkBgeM3::getContent).toList();
    }


    /**
     * 将 float 数组转换为 PostgreSQL pgvector 格式的字符串
     *
     * @param v float 向量数组
     * @return pgvector 格式的字符串（如 [1.0, 2.0, 3.0]）
     */
    private String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(v[i]);
            if (i < v.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Ollama 向量化 API 的响应实体类
     */
    @Data
    private static class EmbeddingResponse {
        /**
         * 文本的向量表示
         */
        private float[] embedding;
    }
}