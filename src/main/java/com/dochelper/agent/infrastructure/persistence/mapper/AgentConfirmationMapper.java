package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentConfirmationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 人工确认 Mapper。
 */
@Mapper
public interface AgentConfirmationMapper extends BaseMapper<AgentConfirmationEntity> {
}
