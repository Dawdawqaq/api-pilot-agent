package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentToolCallEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 工具调用 Mapper。
 */
@Mapper
public interface AgentToolCallMapper extends BaseMapper<AgentToolCallEntity> {
}
