package com.dochelper.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.agent.infrastructure.persistence.entity.AgentTaskEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Agent 任务事件 Mapper。
 */
@Mapper
public interface AgentTaskEventMapper extends BaseMapper<AgentTaskEventEntity> {

    /**
     * 查询任务的下一个事件序号。
     *
     * @param taskId 任务标识
     * @return 下一个事件序号
     */
    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0) + 1
            FROM agent_task_event
            WHERE task_id = #{taskId}
            """)
    long selectNextSequence(@Param("taskId") Long taskId);
}
