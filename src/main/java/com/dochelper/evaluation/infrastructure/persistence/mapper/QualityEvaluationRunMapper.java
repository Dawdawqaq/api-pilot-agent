package com.dochelper.evaluation.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.evaluation.infrastructure.persistence.entity.QualityEvaluationRunEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质量评测 Mapper。
 */
@Mapper
public interface QualityEvaluationRunMapper extends BaseMapper<QualityEvaluationRunEntity> {
}
