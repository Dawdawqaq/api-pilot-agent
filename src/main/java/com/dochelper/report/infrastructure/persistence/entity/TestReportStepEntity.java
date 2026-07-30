package com.dochelper.report.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 测试报告步骤数据库实体。
 */
@TableName("test_report_step")
public class TestReportStepEntity {

    @TableId
    private Long id;
    private Long reportId;
    private Long executionStepId;
    private Integer stepIndex;
    private String stepName;
    private String httpMethod;
    private String requestUrl;
    private String requestHeadersJson;
    private String requestBodyRedacted;
    private Integer responseStatus;
    private String responseHeadersJson;
    private String responseBodyRedacted;
    private String assertionsJson;
    private Boolean success;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getExecutionStepId() { return executionStepId; }
    public void setExecutionStepId(Long executionStepId) {
        this.executionStepId = executionStepId;
    }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestHeadersJson() { return requestHeadersJson; }
    public void setRequestHeadersJson(String requestHeadersJson) {
        this.requestHeadersJson = requestHeadersJson;
    }
    public String getRequestBodyRedacted() { return requestBodyRedacted; }
    public void setRequestBodyRedacted(String requestBodyRedacted) {
        this.requestBodyRedacted = requestBodyRedacted;
    }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }
    public String getResponseHeadersJson() { return responseHeadersJson; }
    public void setResponseHeadersJson(String responseHeadersJson) {
        this.responseHeadersJson = responseHeadersJson;
    }
    public String getResponseBodyRedacted() { return responseBodyRedacted; }
    public void setResponseBodyRedacted(String responseBodyRedacted) {
        this.responseBodyRedacted = responseBodyRedacted;
    }
    public String getAssertionsJson() { return assertionsJson; }
    public void setAssertionsJson(String assertionsJson) {
        this.assertionsJson = assertionsJson;
    }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
