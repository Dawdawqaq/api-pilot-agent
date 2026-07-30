package com.dochelper.executor.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.executor.infrastructure.persistence.entity.ExecutionAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 场景执行审计 Mapper。
 */
@Mapper
public interface ExecutionAuditMapper extends BaseMapper<ExecutionAuditEntity> {
}
