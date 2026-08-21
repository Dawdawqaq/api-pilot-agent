package com.dochelper.contract.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.contract.infrastructure.persistence.entity.TestRunCoverageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试覆盖率 Mapper。
 */
@Mapper
public interface TestRunCoverageMapper extends BaseMapper<TestRunCoverageEntity> {
}
