package com.kaer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.converter.AgentConverter;
import com.kaer.exception.BizException;
import com.kaer.mapper.AgentMapper;
import com.kaer.model.dto.AgentDTO;
import com.kaer.model.entity.Agent;
import com.kaer.model.request.CreateAgentRequest;
import com.kaer.model.request.UpdateAgentRequest;
import com.kaer.model.response.CreateAgentResponse;
import com.kaer.model.response.GetAgentsResponse;
import com.kaer.model.vo.AgentVO;
import com.kaer.service.AgentFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@AllArgsConstructor
public class AgentFacadeServiceImpl implements AgentFacadeService {

    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;


    /**
     * 创建智能代理
     * <p>根据请求参数创建一个新的智能代理，执行请求转换、实体构建、数据持久化等操作</p>
     *
     * @param request 创建代理请求，包含代理的名称、描述、模型类型等配置信息
     * @return CreateAgentResponse 创建响应，包含自动生成的代理唯一标识
     * @throws BizException 当数据库插入失败或序列化异常时抛出业务异常
     */
    @Override
    public CreateAgentResponse createAgent(CreateAgentRequest request) {
        try {
            // 将请求对象转换为数据传输对象
            AgentDTO agentDTO = agentConverter.toDTO(request);
            // 将DTO转换为数据库实体对象
            Agent agent = agentConverter.toEntity(agentDTO);

            // 设置创建时间和更新时间为当前时间
            LocalDateTime now = LocalDateTime.now();
            agent.setCreatedAt(now);
            agent.setUpdatedAt(now);

            // 插入数据库，返回影响的行数
            int result = agentMapper.insert(agent);
            if (result <= 0) {
                throw new BizException("创建 agent 失败");
            }

            // 返回生成的代理ID
            return CreateAgentResponse.builder()
                    .agentId(agent.getId())
                    .build();
        } catch (JsonProcessingException e) {
            throw new BizException("创建 agent 时发生序列化错误: " + e.getMessage());
        }
    }

    /**
     * 获取所有智能代理列表
     * <p>查询数据库中所有的智能代理记录，转换为视图对象后返回</p>
     *
     * @return GetAgentsResponse 包含所有代理列表的响应对象
     * @throws RuntimeException 当序列化异常时抛出运行时异常
     */
    @Override
    public GetAgentsResponse getAgents() {
        // 查询所有代理实体
        List<Agent> agents = agentMapper.selectAll();
        List<AgentVO> result = new ArrayList<>();

        // 遍历转换为视图对象
        for (Agent agent : agents) {
            try {
                AgentVO vo = agentConverter.toVO(agent);
                result.add(vo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        // 构建并返回响应
        return GetAgentsResponse.builder()
                .agents(result.toArray(new AgentVO[0]))
                .build();
    }


    @Override
    public void deleteAgent(String agentId) {
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BizException("Agent 不存在: " + agentId);
        }

        int result = agentMapper.deleteById(agentId);
        if (result <= 0) {
            throw new BizException("删除 agent 失败");
        }
    }

    /**
     * 更新智能代理信息
     * <p>根据代理ID更新代理的配置信息，包括查询验证、数据转换、更新持久化等操作</p>
     *
     * @param agentId 代理唯一标识
     * @param request 更新请求，包含要更新的代理配置信息
     * @throws BizException 当代理不存在、更新失败或序列化异常时抛出业务异常
     */
    @Override
    public void updateAgent(String agentId, UpdateAgentRequest request) {
        try {
            // 根据ID查询代理实体
            Agent agent = agentMapper.selectById(agentId);
            if (agent == null) {
                throw new BizException("Agent 不存在: " + agentId);
            }

            // 将现有代理实体转换为DTO
            AgentDTO agentDTO = agentConverter.toDTO(agent);
            // 根据请求更新DTO字段
            agentConverter.updateDTOFromRequest(agentDTO, request);
            // 将更新后的DTO转换回实体
            Agent updatedAgent = agentConverter.toEntity(agentDTO);

            // 保留原有的ID和创建时间
            updatedAgent.setId(agentId);
            updatedAgent.setCreatedAt(agent.getCreatedAt());
            // 更新更新时间为当前时间
            updatedAgent.setUpdatedAt(LocalDateTime.now());

            // 更新数据库记录
            int result = agentMapper.updateById(updatedAgent);
            if (result <= 0) {
                throw new BizException("更新 agent 失败");
            }

        } catch (JsonProcessingException e) {
            throw new BizException("更新 agent 时发生序列化错误: " + e.getMessage());
        }
    }

}