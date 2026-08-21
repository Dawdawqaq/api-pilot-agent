package com.dochelper.contract.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 契约操作结果数据库实体。
 */
@TableName("contract_operation_result")
public class ContractOperationResultEntity {
    @TableId private Long id;
    private Long projectId;
    private Long executionId;
    private Integer stepIndex;
    private Long endpointId;
    private String operationId;
    private String httpMethod;
    private String pathTemplate;
    private Integer responseStatus;
    private Integer contractRulesTotal;
    private Integer contractRulesCovered;
    private String violationsJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    public Long getEndpointId() { return endpointId; }
    public void setEndpointId(Long endpointId) { this.endpointId = endpointId; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getPathTemplate() { return pathTemplate; }
    public void setPathTemplate(String pathTemplate) { this.pathTemplate = pathTemplate; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public Integer getContractRulesTotal() { return contractRulesTotal; }
    public void setContractRulesTotal(Integer contractRulesTotal) { this.contractRulesTotal = contractRulesTotal; }
    public Integer getContractRulesCovered() { return contractRulesCovered; }
    public void setContractRulesCovered(Integer contractRulesCovered) { this.contractRulesCovered = contractRulesCovered; }
    public String getViolationsJson() { return violationsJson; }
    public void setViolationsJson(String violationsJson) { this.violationsJson = violationsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
