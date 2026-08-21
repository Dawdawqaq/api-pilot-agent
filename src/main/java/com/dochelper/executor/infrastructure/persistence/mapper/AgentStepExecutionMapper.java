package com.dochelper.executor.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.executor.infrastructure.persistence.entity.AgentStepExecutionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Agent 单步执行 Mapper。
 */
@Mapper
public interface AgentStepExecutionMapper extends BaseMapper<AgentStepExecutionEntity> {

    @Select("SELECT COALESCE(MAX(attempt), 0) + 1 FROM agent_step_execution "
            + "WHERE task_id = #{taskId} AND step_index = #{stepIndex}")
    int selectNextAttempt(@Param("taskId") Long taskId, @Param("stepIndex") int stepIndex);
}
