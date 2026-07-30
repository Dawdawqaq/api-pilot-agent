package com.dochelper.agent.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Agent 工具调用数据库实体。
 */
@TableName("agent_tool_call")
public class AgentToolCallEntity {

    @TableId
    private Long id;
    private Long taskId;
    private Integer stepIndex;
    private String toolName;
    private String callKey;
    private String requestJsonRedacted;
    private String responseJsonRedacted;
    private String status;
    private Integer attempt;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getCallKey() { return callKey; }
    public void setCallKey(String callKey) { this.callKey = callKey; }
    public String getRequestJsonRedacted() { return requestJsonRedacted; }
    public void setRequestJsonRedacted(String requestJsonRedacted) {
        this.requestJsonRedacted = requestJsonRedacted;
    }
    public String getResponseJsonRedacted() { return responseJsonRedacted; }
    public void setResponseJsonRedacted(String responseJsonRedacted) {
        this.responseJsonRedacted = responseJsonRedacted;
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
