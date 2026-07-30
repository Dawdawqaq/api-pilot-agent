package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务 Mapper。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEntity> {
}
