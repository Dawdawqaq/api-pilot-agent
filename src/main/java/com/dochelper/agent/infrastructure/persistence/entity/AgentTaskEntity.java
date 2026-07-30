package com.dochelper.agent.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Agent 任务数据库实体。
 */
@TableName("agent_task")
public class AgentTaskEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Long environmentId;
    private Long conversationId;
    private String goal;
    private String status;
    private Integer currentStep;
    private Integer maxSteps;
    private Integer toolCallCount;
    private Integer replanCount;
    private String planJson;
    private String contextJsonRedacted;
    private String resultSummary;
    private String errorCode;
    private String errorMessage;
    private Boolean cancelRequested;
    private Integer lockVersion;
    private LocalDateTime deadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(Long environmentId) { this.environmentId = environmentId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public Integer getMaxSteps() { return maxSteps; }
    public void setMaxSteps(Integer maxSteps) { this.maxSteps = maxSteps; }
    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    public Integer getReplanCount() { return replanCount; }
    public void setReplanCount(Integer replanCount) { this.replanCount = replanCount; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getContextJsonRedacted() { return contextJsonRedacted; }
    public void setContextJsonRedacted(String contextJsonRedacted) {
        this.contextJsonRedacted = contextJsonRedacted;
    }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Boolean getCancelRequested() { return cancelRequested; }
    public void setCancelRequested(Boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }
    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
