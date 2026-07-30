package com.dochelper.retrieval.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.retrieval.infrastructure.persistence.entity.EvaluationRunEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检索评测运行 Mapper。
 */
@Mapper
public interface EvaluationRunMapper extends BaseMapper<EvaluationRunEntity> {
}
