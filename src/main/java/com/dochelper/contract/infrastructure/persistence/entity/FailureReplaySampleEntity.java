package com.dochelper.contract.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 失败回放样本数据库实体。
 */
@TableName("failure_replay_sample")
public class FailureReplaySampleEntity {
    @TableId private Long id;
    private Long projectId;
    private Long executionId;
    private Integer stepIndex;
    private String requestFingerprint;
    private String requestJsonRedacted;
    private String requestSecretRef;
    private String errorSummary;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getRequestJsonRedacted() { return requestJsonRedacted; }
    public void setRequestJsonRedacted(String requestJsonRedacted) { this.requestJsonRedacted = requestJsonRedacted; }
    public String getRequestSecretRef() { return requestSecretRef; }
    public void setRequestSecretRef(String requestSecretRef) { this.requestSecretRef = requestSecretRef; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
