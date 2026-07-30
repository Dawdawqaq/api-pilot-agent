package com.dochelper.retrieval.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.retrieval.infrastructure.persistence.entity.EvaluationCaseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检索评测用例 Mapper。
 */
@Mapper
public interface EvaluationCaseMapper extends BaseMapper<EvaluationCaseEntity> {
}
