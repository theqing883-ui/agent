package com.kaer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.converter.KnowledgeBaseConverter;
import com.kaer.exception.BizException;
import com.kaer.mapper.ChunkBgeM3Mapper;
import com.kaer.mapper.KnowledgeBaseMapper;
import com.kaer.model.dto.KnowledgeBaseDTO;
import com.kaer.model.entity.ChunkBgeM3;
import com.kaer.model.entity.KnowledgeBase;
import com.kaer.model.request.CreateKnowledgeBaseRequest;
import com.kaer.model.request.UpdateKnowledgeBaseRequest;
import com.kaer.model.response.CreateKnowledgeBaseResponse;
import com.kaer.model.response.GetKnowledgeBasesResponse;
import com.kaer.model.vo.KnowledgeBaseVO;
import com.kaer.service.KnowledgeBaseFacadeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class KnowledgeBaseFacadeServiceImpl implements KnowledgeBaseFacadeService {
    private final KnowledgeBaseConverter knowledgeBaseConverter;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final RedisVectorService redisVectorService;


    /**
     * 创建知识库
     * 将请求参数转换为实体并持久化到数据库，返回创建的知识库ID
     *
     * @param request 创建知识库请求对象，包含知识库基本信息
     * @return CreateKnowledgeBaseResponse 创建结果响应，包含新建知识库的ID
     * @throws BizException     当数据库插入失败时抛出业务异常
     * @throws RuntimeException 当JSON处理失败时抛出运行时异常
     */
    @Override

    public CreateKnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request) {
        try {
            // 将请求对象转换为DTO（数据传输对象）
            KnowledgeBaseDTO knowledgeBaseDTO = knowledgeBaseConverter.toDTO(request);
            // 将DTO转换为实体对象（用于数据库持久化）
            KnowledgeBase knowledgeBase = knowledgeBaseConverter.toEntity(knowledgeBaseDTO);

            // 设置创建时间为当前时间
            knowledgeBase.setCreatedAt(LocalDateTime.now());
            // 设置更新时间为当前时间
            knowledgeBase.setUpdatedAt(LocalDateTime.now());

            // 插入数据库并获取影响行数
            int result = knowledgeBaseMapper.insert(knowledgeBase);
            // 判断插入是否成功
            if (result <= 0) {
                throw new BizException("创建知识库失败");
            }
            // 构建响应对象，返回新建知识库的ID
            return CreateKnowledgeBaseResponse.builder().knowledgeBaseId(knowledgeBase.getId()).build();
        } catch (JsonProcessingException e) {
            // JSON序列化/反序列化异常，包装为运行时异常抛出
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询所有知识库列表
     * 从数据库查询所有知识库记录，转换为VO列表后返回
     *
     * @return GetKnowledgeBasesResponse 知识库列表响应，包含所有知识库的VO对象数组
     * @throws RuntimeException 当JSON处理失败时抛出运行时异常
     */
    @Override
    public GetKnowledgeBasesResponse getKnowledgeBases() {
        // 查询数据库获取所有知识库实体列表
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectAll();
        // 初始化VO结果列表
        List<KnowledgeBaseVO> result = new ArrayList<>();
        // 遍历实体列表，逐个转换为VO对象
        for (KnowledgeBase knowledgeBase : knowledgeBases) {
            try {
                result.add(knowledgeBaseConverter.toVO(knowledgeBase));
            } catch (JsonProcessingException e) {
                // JSON序列化/反序列化异常，包装为运行时异常抛出
                throw new RuntimeException(e);
            }
        }
        // 构建响应对象，将VO列表转换为数组返回
        return GetKnowledgeBasesResponse.builder().knowledgeBases(result.toArray(new KnowledgeBaseVO[0])).build();
    }

    /**
     * 删除知识库
     *
     * @param knowledgeBaseId 知识库ID
     * @throws BizException 当知识库不存在或删除失败时抛出
     */
    @Override
    public void deleteKnowledgeBase(String knowledgeBaseId) {
        // 查询知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BizException("知识库不存在: " + knowledgeBaseId);
        }

        // 删除Redis中该知识库关联的向量数据
        try {
            List<ChunkBgeM3> chunkBgeM3s = chunkBgeM3Mapper.selectByKbId(knowledgeBaseId);
            if (chunkBgeM3s != null && !chunkBgeM3s.isEmpty()) {
                List<String> chunkIds = chunkBgeM3s.stream().map(ChunkBgeM3::getId).toList();
                redisVectorService.deleteVector(chunkIds);
                log.info("已删除Redis向量数据: kbId={}, count={}", knowledgeBaseId, chunkIds.size());
            }
        } catch (Exception e) {
            log.warn("删除Redis向量数据失败，继续删除知识库记录: kbId={}, error={}", knowledgeBaseId, e.getMessage());
        }

        // 执行删除操作（CASCADE会自动清理chunk_bge_m3表）
        int result = knowledgeBaseMapper.deleteById(knowledgeBaseId);
        if (result <= 0) {
            throw new BizException("删除知识库失败: " + knowledgeBaseId);
        }
    }

    /**
     * 更新知识库信息
     * 流程：查询知识库 -> 转换为DTO -> 根据请求更新DTO -> 转换为实体 -> 更新数据库
     *
     * @param knowledgeBaseId 知识库ID
     * @param request         更新知识库请求对象
     * @throws BizException     当知识库不存在或更新失败时抛出
     * @throws RuntimeException 当JSON处理失败时抛出
     */
    @Override
    public void updateKnowledgeBase(String knowledgeBaseId, UpdateKnowledgeBaseRequest request) {
        try {
            // 1. 根据ID查询知识库
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);

            // 2. 检查知识库是否存在
            if (knowledgeBase == null) {
                throw new BizException("知识库不存在: " + knowledgeBaseId);
            }

            // 3. 将实体转换为DTO
            KnowledgeBaseDTO knowledgeBaseDTO = knowledgeBaseConverter.toDTO(knowledgeBase);

            // 4. 根据请求更新DTO字段
            knowledgeBaseConverter.updateDTOFromRequest(knowledgeBaseDTO, request);

            // 5. 将更新后的DTO转换为实体
            KnowledgeBase updatedKnowledgeBase = knowledgeBaseConverter.toEntity(knowledgeBaseDTO);

            // 6. 设置必要的字段
            updatedKnowledgeBase.setId(knowledgeBaseId);              // 设置ID
            updatedKnowledgeBase.setUpdatedAt(LocalDateTime.now());   // 更新时间设为当前时间
            updatedKnowledgeBase.setCreatedAt(knowledgeBase.getCreatedAt()); // 保持原创建时间不变

            // 7. 更新数据库记录
            int result = knowledgeBaseMapper.updateById(updatedKnowledgeBase);
            if (result <= 0) {
                throw new BizException("更新知识库失败: " + knowledgeBaseId);
            }
        } catch (JsonProcessingException e) {
            // JSON序列化/反序列化异常，包装为运行时异常抛出
            throw new RuntimeException(e);
        }
    }
}