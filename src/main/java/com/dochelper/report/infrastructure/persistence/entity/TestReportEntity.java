package com.dochelper.report.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 测试报告数据库实体。
 */
@TableName("test_report")
public class TestReportEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long executionId;
    private String title;
    private String status;
    private String summary;
    private Integer totalSteps;
    private Integer passedSteps;
    private Integer failedSteps;
    private Integer totalToolCalls;
    private Long durationMs;
    private String evidenceJson;
    private String metricsJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }
    public Integer getPassedSteps() { return passedSteps; }
    public void setPassedSteps(Integer passedSteps) { this.passedSteps = passedSteps; }
    public Integer getFailedSteps() { return failedSteps; }
    public void setFailedSteps(Integer failedSteps) { this.failedSteps = failedSteps; }
    public Integer getTotalToolCalls() { return totalToolCalls; }
    public void setTotalToolCalls(Integer totalToolCalls) {
        this.totalToolCalls = totalToolCalls;
    }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
