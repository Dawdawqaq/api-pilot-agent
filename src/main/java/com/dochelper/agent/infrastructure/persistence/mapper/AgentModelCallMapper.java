package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentModelCallEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 模型调用指标 Mapper。
 */
@Mapper
public interface AgentModelCallMapper extends BaseMapper<AgentModelCallEntity> {
}
