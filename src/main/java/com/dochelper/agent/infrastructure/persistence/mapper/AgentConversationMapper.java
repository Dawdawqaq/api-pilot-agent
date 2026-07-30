package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentConversationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 会话 Mapper。
 */
@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversationEntity> {
}
