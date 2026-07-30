package com.dochelper.executor.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.executor.infrastructure.persistence.entity.ExecutionStepAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 场景执行步骤审计 Mapper。
 */
@Mapper
public interface ExecutionStepAuditMapper extends BaseMapper<ExecutionStepAuditEntity> {
}
