package com.dochelper.contract.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.contract.infrastructure.persistence.entity.ContractOperationResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 契约结果 Mapper。
 */
@Mapper
public interface ContractOperationResultMapper extends BaseMapper<ContractOperationResultEntity> {
}
