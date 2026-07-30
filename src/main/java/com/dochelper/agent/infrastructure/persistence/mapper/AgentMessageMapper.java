package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 会话消息 Mapper。
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessageEntity> {
}
