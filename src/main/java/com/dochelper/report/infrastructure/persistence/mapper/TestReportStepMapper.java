package com.dochelper.report.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.report.infrastructure.persistence.entity.TestReportStepEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试报告步骤 Mapper。
 */
@Mapper
public interface TestReportStepMapper extends BaseMapper<TestReportStepEntity> {
}
