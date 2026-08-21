package com.dochelper.contract.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.contract.infrastructure.persistence.entity.FailureReplaySampleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 失败回放 Mapper。
 */
@Mapper
public interface FailureReplaySampleMapper extends BaseMapper<FailureReplaySampleEntity> {
}
