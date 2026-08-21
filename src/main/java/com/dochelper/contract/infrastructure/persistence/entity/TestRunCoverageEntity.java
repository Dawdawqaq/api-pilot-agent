package com.dochelper.contract.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 测试覆盖率数据库实体。
 */
@TableName("test_run_coverage")
public class TestRunCoverageEntity {
    @TableId private Long id;
    private Long projectId;
    private Long reportId;
    private Long executionId;
    private Integer operationTotal;
    private Integer operationCovered;
    private Integer methodTotal;
    private Integer methodCovered;
    private Integer documentedStatusTotal;
    private Integer statusCovered;
    private Integer schemaRulesTotal;
    private Integer schemaRulesCovered;
    private Integer uniqueServerErrorCount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public Integer getOperationTotal() { return operationTotal; }
    public void setOperationTotal(Integer operationTotal) { this.operationTotal = operationTotal; }
    public Integer getOperationCovered() { return operationCovered; }
    public void setOperationCovered(Integer operationCovered) { this.operationCovered = operationCovered; }
    public Integer getMethodTotal() { return methodTotal; }
    public void setMethodTotal(Integer methodTotal) { this.methodTotal = methodTotal; }
    public Integer getMethodCovered() { return methodCovered; }
    public void setMethodCovered(Integer methodCovered) { this.methodCovered = methodCovered; }
    public Integer getDocumentedStatusTotal() { return documentedStatusTotal; }
    public void setDocumentedStatusTotal(Integer documentedStatusTotal) { this.documentedStatusTotal = documentedStatusTotal; }
    public Integer getStatusCovered() { return statusCovered; }
    public void setStatusCovered(Integer statusCovered) { this.statusCovered = statusCovered; }
    public Integer getSchemaRulesTotal() { return schemaRulesTotal; }
    public void setSchemaRulesTotal(Integer schemaRulesTotal) { this.schemaRulesTotal = schemaRulesTotal; }
    public Integer getSchemaRulesCovered() { return schemaRulesCovered; }
    public void setSchemaRulesCovered(Integer schemaRulesCovered) { this.schemaRulesCovered = schemaRulesCovered; }
    public Integer getUniqueServerErrorCount() { return uniqueServerErrorCount; }
    public void setUniqueServerErrorCount(Integer uniqueServerErrorCount) { this.uniqueServerErrorCount = uniqueServerErrorCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
