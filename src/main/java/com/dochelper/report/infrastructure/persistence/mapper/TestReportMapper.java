package com.dochelper.report.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.report.infrastructure.persistence.entity.TestReportEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试报告 Mapper。
 */
@Mapper
public interface TestReportMapper extends BaseMapper<TestReportEntity> {
}
