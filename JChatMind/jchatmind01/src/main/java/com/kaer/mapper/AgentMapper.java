package com.kaer.mapper;

import com.kaer.model.entity.Agent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


/**
 * Agent数据访问接口
 * <p>基于MyBatis的Agent实体数据访问层，提供Agent的CRUD操作</p>
 */
@Mapper
public interface AgentMapper {

    /**
     * 插入Agent记录
     *
     * @param agent Agent实体对象
     * @return 影响的行数
     */
    int insert(Agent agent);

    /**
     * 根据ID查询Agent
     *
     * @param id Agent唯一标识
     * @return Agent实体对象，如果不存在返回null
     */
    Agent selectById(String id);

    /**
     * 查询所有Agent记录
     *
     * @return Agent列表
     */
    List<Agent> selectAll();

    /**
     * 根据ID删除Agent
     *
     * @param id Agent唯一标识
     * @return 影响的行数
     */
    int deleteById(String id);

    /**
     * 根据ID更新Agent
     *
     * @param agent Agent实体对象
     * @return 影响的行数
     */
    int updateById(Agent agent);
}