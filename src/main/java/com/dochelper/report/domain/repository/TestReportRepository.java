package com.dochelper.report.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.report.domain.TestReport;
import com.dochelper.report.domain.TestReportStep;

/**
 * 测试报告仓储。
 */
public interface TestReportRepository {

    TestReport create(TestReport report, List<TestReportStep> steps);

    Optional<TestReport> findByTaskId(Long taskId);

    Optional<TestReport> findByProjectAndId(Long projectId, Long reportId);

    List<TestReport> findByProjectId(Long projectId, int limit);

    List<TestReportStep> findSteps(Long reportId);
}
