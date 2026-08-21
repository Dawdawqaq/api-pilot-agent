package com.dochelper.evaluation.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 质量评测数据库实体。
 */
@TableName("quality_evaluation_run")
public class QualityEvaluationRunEntity {
    @TableId private Long id;
    private String datasetVersion;
    private Integer serviceCount;
    private Integer evaluationCaseCount;
    private Integer securityCaseCount;
    private Integer passedCaseCount;
    private Integer blockedAttackCount;
    private Double taskSuccessRate;
    private Double validPlanRate;
    private Double securityBlockRate;
    private Long p95TaskDurationMs;
    private Long totalModelTokens;
    private String metricsJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }
    public Integer getServiceCount() { return serviceCount; }
    public void setServiceCount(Integer serviceCount) { this.serviceCount = serviceCount; }
    public Integer getEvaluationCaseCount() { return evaluationCaseCount; }
    public void setEvaluationCaseCount(Integer evaluationCaseCount) { this.evaluationCaseCount = evaluationCaseCount; }
    public Integer getSecurityCaseCount() { return securityCaseCount; }
    public void setSecurityCaseCount(Integer securityCaseCount) { this.securityCaseCount = securityCaseCount; }
    public Integer getPassedCaseCount() { return passedCaseCount; }
    public void setPassedCaseCount(Integer passedCaseCount) { this.passedCaseCount = passedCaseCount; }
    public Integer getBlockedAttackCount() { return blockedAttackCount; }
    public void setBlockedAttackCount(Integer blockedAttackCount) { this.blockedAttackCount = blockedAttackCount; }
    public Double getTaskSuccessRate() { return taskSuccessRate; }
    public void setTaskSuccessRate(Double taskSuccessRate) { this.taskSuccessRate = taskSuccessRate; }
    public Double getValidPlanRate() { return validPlanRate; }
    public void setValidPlanRate(Double validPlanRate) { this.validPlanRate = validPlanRate; }
    public Double getSecurityBlockRate() { return securityBlockRate; }
    public void setSecurityBlockRate(Double securityBlockRate) { this.securityBlockRate = securityBlockRate; }
    public Long getP95TaskDurationMs() { return p95TaskDurationMs; }
    public void setP95TaskDurationMs(Long p95TaskDurationMs) { this.p95TaskDurationMs = p95TaskDurationMs; }
    public Long getTotalModelTokens() { return totalModelTokens; }
    public void setTotalModelTokens(Long totalModelTokens) { this.totalModelTokens = totalModelTokens; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
