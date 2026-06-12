package com.kaer.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis向量服务类
 * 基于Redis Stack实现向量存储、检索和管理功能
 * 使用HNSW算法进行高效的近似最近邻搜索
 */
@Slf4j
//@Service
public class RedisVectorService {

    /**
     * Redis Hash键前缀，用于存储向量数据
     */
    private static final String KEY_PREFIX = "chunk:";

    /**
     * 向量索引名称a
     */
    private static final String INDEX_NAME = "idx:chunk_bge_m3";

    /**
     * 向量维度，对应BGE-M3模型的输出维度
     */
    private static final int VECTOR_DIM = 1024;

    /**
     * Redis客户端实例
     */
    private final JedisPooled jedis;

    /**
     * 构造函数，注入JedisPooled客户端
     *
     * @param jedis Redis连接池客户端
     */
    public RedisVectorService(JedisPooled jedis) {
        this.jedis = jedis;
    }

    /**
     * 初始化方法，在Bean创建后自动执行
     * 检查向量索引是否存在，不存在则创建
     */
    @PostConstruct
    public void init() {
        try {
            // 获取所有已存在的索引
            Set<String> indexes = jedis.ftList();
            if (!indexes.contains(INDEX_NAME)) {
                // 创建向量索引
                createIndex();
                log.info("Created Redis vector index: {}", INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("Failed to check/create Redis index (ensure Redis Stack is running): {}", e.getMessage());
        }
    }

    /**
     * 创建Redis向量索引
     * 使用HNSW算法，支持余弦相似度度量
     */
    private void createIndex() {
        jedis.ftCreate(INDEX_NAME,
                // 设置索引类型为HASH，键前缀为"chunk:"
                FTCreateParams.createParams()
                        .on(IndexDataType.HASH)
                        .prefix(KEY_PREFIX),
                // 定义向量字段
                VectorField.builder()
                        .fieldName("embedding")
                        .algorithm(VectorField.VectorAlgorithm.HNSW)
                        .attributes(Map.of(
                                "TYPE", "FLOAT32",           // 向量数据类型
                                "DIM", VECTOR_DIM,           // 向量维度
                                "DISTANCE_METRIC", "COSINE", // 距离度量方式
                                "M", 16,                    // HNSW图的最大连接数，决定了每个节点在图中最多能连多少条边。
                                "EF_CONSTRUCTION", 200       // 构建时的候选列表大小，决定了在查询阶段，每一层贪心搜索时候选队列的长度
                        ))
                        .build(),
                // 定义知识库ID标签字段，构建了一个倒排索引,用于根据知识库ID筛选
                TagField.of("kb_id")
        );
    }

    /**
     * 存储向量数据到Redis
     *
     * @param chunkId   文档片段ID
     * @param embedding 向量嵌入数据
     * @param kbId      知识库ID
     */
    public void storeVector(String chunkId, float[] embedding, String kbId) {
        // 将float数组转换为字节数组
        byte[] embBytes = floatsToBytes(embedding);
        Map<byte[], byte[]> hash = new LinkedHashMap<>();
        hash.put("embedding".getBytes(), embBytes);
        hash.put("kb_id".getBytes(), kbId.getBytes());
        // 存储到Redis Hash
        jedis.hset((KEY_PREFIX + chunkId).getBytes(), hash);
    }

    /**
     * 在指定知识库中搜索相似向量
     *
     * @param kbId        知识库ID，用于筛选
     * @param queryVector 查询向量
     * @param topK        返回最相似的前K个结果
     * @return 匹配的文档片段ID列表
     */
    public List<String> search(String kbId, float[] queryVector, int topK) {
        // 转义知识库 ID 中的特殊字符
        // 将 "74a...-4d..." 变成 "74a...\-4d..."
        String escapedKbId = kbId.replace("-", "\\-");
        // 将查询向量转换为字节数组
        byte[] blob = floatsToBytes(queryVector);
        // 构建KNN查询语句，筛选出指定库中的向量，先根据知识库ID筛选出符合条件的Hash数据，再计算与查询向量的相似度分数
        // 并根据相似度分数排序，返回前K个结果
        // @kb_id:{%s}=>[KNN %d @embedding $BLOB AS score]
        String queryStr = String.format("@kb_id:{%s}=>[KNN %d @embedding $BLOB AS score]", escapedKbId, topK);

        Query query = new Query(queryStr);
        query.addParam("BLOB", blob);      // 设置查询向量参数
        query.setSortBy("score", true);    // 按相似度分数升序排序
        query.dialect(2);                   // 使用Redis Search v2语法
        query.limit(0, topK);              // 限制返回数量

        // 执行向量搜索
        SearchResult result = jedis.ftSearch(INDEX_NAME, query);

        // 提取文档ID并去除前缀
        return result.getDocuments().stream()
                .map(doc -> doc.getId().substring(KEY_PREFIX.length()))
                .toList();
    }



    /**
     * 删除指定的向量数据
     *
     * @param chunkIds 文档片段ID列表
     */
    public void deleteVector(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String[] keys = chunkIds.stream()
                .map(id -> KEY_PREFIX + id)
                .toArray(String[]::new);

        jedis.del(keys);
    }

    /**
     * 将float数组转换为小端序字节数组
     *
     * @param floats float数组
     * @return 字节数组
     */
    private byte[] floatsToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}