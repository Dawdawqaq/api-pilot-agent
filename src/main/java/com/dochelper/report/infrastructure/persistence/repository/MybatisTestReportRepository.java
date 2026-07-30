package com.dochelper.report.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.report.domain.TestReport;
import com.dochelper.report.domain.TestReportStep;
import com.dochelper.report.domain.repository.TestReportRepository;
import com.dochelper.report.infrastructure.persistence.entity.TestReportEntity;
import com.dochelper.report.infrastructure.persistence.entity.TestReportStepEntity;
import com.dochelper.report.infrastructure.persistence.mapper.TestReportMapper;
import com.dochelper.report.infrastructure.persistence.mapper.TestReportStepMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis-Plus 的测试报告仓储。
 */
@Repository
public class MybatisTestReportRepository implements TestReportRepository {

    private final TestReportMapper reportMapper;
    private final TestReportStepMapper stepMapper;
    private final ObjectMapper objectMapper;

    public MybatisTestReportRepository(
            TestReportMapper reportMapper,
            TestReportStepMapper stepMapper,
            ObjectMapper objectMapper
    ) {
        this.reportMapper = reportMapper;
        this.stepMapper = stepMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TestReport create(TestReport report, List<TestReportStep> steps) {
        reportMapper.insert(toEntity(report));
        steps.forEach(step -> stepMapper.insert(toEntity(step)));
        return findByProjectAndId(report.projectId(), report.id()).orElseThrow();
    }

    @Override
    public Optional<TestReport> findByTaskId(Long taskId) {
        return Optional.ofNullable(reportMapper.selectOne(
                Wrappers.<TestReportEntity>lambdaQuery()
                        .eq(TestReportEntity::getTaskId, taskId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public Optional<TestReport> findByProjectAndId(Long projectId, Long reportId) {
        return Optional.ofNullable(reportMapper.selectOne(
                Wrappers.<TestReportEntity>lambdaQuery()
                        .eq(TestReportEntity::getId, reportId)
                        .eq(TestReportEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<TestReport> findByProjectId(Long projectId, int limit) {
        return reportMapper.selectList(
                Wrappers.<TestReportEntity>lambdaQuery()
                        .eq(TestReportEntity::getProjectId, projectId)
                        .orderByDesc(TestReportEntity::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100)))
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public List<TestReportStep> findSteps(Long reportId) {
        return stepMapper.selectList(
                Wrappers.<TestReportStepEntity>lambdaQuery()
                        .eq(TestReportStepEntity::getReportId, reportId)
                        .orderByAsc(TestReportStepEntity::getStepIndex)
        ).stream().map(this::toDomain).toList();
    }

    private TestReportEntity toEntity(TestReport value) {
        TestReportEntity entity = new TestReportEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setTaskId(value.taskId());
        entity.setExecutionId(value.executionId());
        entity.setTitle(value.title());
        entity.setStatus(value.status());
        entity.setSummary(value.summary());
        entity.setTotalSteps(value.totalSteps());
        entity.setPassedSteps(value.passedSteps());
        entity.setFailedSteps(value.failedSteps());
        entity.setTotalToolCalls(value.totalToolCalls());
        entity.setDurationMs(value.durationMs());
        entity.setEvidenceJson(toJson(value.evidenceCitations()));
        entity.setMetricsJson(value.metricsJson());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private TestReportStepEntity toEntity(TestReportStep value) {
        TestReportStepEntity entity = new TestReportStepEntity();
        entity.setId(value.id());
        entity.setReportId(value.reportId());
        entity.setExecutionStepId(value.executionStepId());
        entity.setStepIndex(value.stepIndex());
        entity.setStepName(value.stepName());
        entity.setHttpMethod(value.httpMethod());
        entity.setRequestUrl(value.requestUrl());
        entity.setRequestHeadersJson(value.requestHeadersJson());
        entity.setRequestBodyRedacted(value.requestBodyRedacted());
        entity.setResponseStatus(value.responseStatus());
        entity.setResponseHeadersJson(value.responseHeadersJson());
        entity.setResponseBodyRedacted(value.responseBodyRedacted());
        entity.setAssertionsJson(value.assertionsJson());
        entity.setSuccess(value.success());
        entity.setDurationMs(value.durationMs());
        entity.setErrorMessage(value.errorMessage());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private TestReport toDomain(TestReportEntity entity) {
        return new TestReport(
                entity.getId(),
                entity.getProjectId(),
                entity.getTaskId(),
                entity.getExecutionId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getSummary(),
                entity.getTotalSteps(),
                entity.getPassedSteps(),
                entity.getFailedSteps(),
                entity.getTotalToolCalls(),
                entity.getDurationMs(),
                fromJson(entity.getEvidenceJson()),
                entity.getMetricsJson(),
                entity.getCreatedAt()
        );
    }

    private TestReportStep toDomain(TestReportStepEntity entity) {
        return new TestReportStep(
                entity.getId(),
                entity.getReportId(),
                entity.getExecutionStepId(),
                entity.getStepIndex(),
                entity.getStepName(),
                entity.getHttpMethod(),
                entity.getRequestUrl(),
                entity.getRequestHeadersJson(),
                entity.getRequestBodyRedacted(),
                entity.getResponseStatus(),
                entity.getResponseHeadersJson(),
                entity.getResponseBodyRedacted(),
                entity.getAssertionsJson(),
                Boolean.TRUE.equals(entity.getSuccess()),
                entity.getDurationMs(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试报告 JSON 序列化失败", exception);
        }
    }

    private List<String> fromJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试报告证据数据损坏", exception);
        }
    }
}
