package com.dochelper.openapi.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * OpenAPI 接口数据库实体。
 */
@TableName("api_endpoint")
public class ApiEndpointEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Long importId;
    private String path;
    private String httpMethod;
    private String operationId;
    private String summary;
    private String description;
    private String tagsJson;
    private Boolean deprecated;
    private String requestBodyJson;
    private String responsesJson;
    private String securityJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public Boolean getDeprecated() { return deprecated; }
    public void setDeprecated(Boolean deprecated) { this.deprecated = deprecated; }
    public String getRequestBodyJson() { return requestBodyJson; }
    public void setRequestBodyJson(String requestBodyJson) { this.requestBodyJson = requestBodyJson; }
    public String getResponsesJson() { return responsesJson; }
    public void setResponsesJson(String responsesJson) { this.responsesJson = responsesJson; }
    public String getSecurityJson() { return securityJson; }
    public void setSecurityJson(String securityJson) { this.securityJson = securityJson; }
}
