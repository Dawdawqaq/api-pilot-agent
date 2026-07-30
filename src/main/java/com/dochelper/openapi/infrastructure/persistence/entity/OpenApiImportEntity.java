package com.dochelper.openapi.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * OpenAPI 导入数据库实体。
 */
@TableName("openapi_import")
public class OpenApiImportEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Integer revisionNumber;
    private Long retryOfId;
    private String fileName;
    private String contentType;
    private String contentHash;
    private String rawContent;
    private String specificationVersion;
    private String documentTitle;
    private String documentVersion;
    private String status;
    private String errorMessage;
    private Integer endpointCount;
    private Integer schemaCount;
    private Integer securitySchemeCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }
    public Long getRetryOfId() { return retryOfId; }
    public void setRetryOfId(Long retryOfId) { this.retryOfId = retryOfId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }
    public String getSpecificationVersion() { return specificationVersion; }
    public void setSpecificationVersion(String specificationVersion) { this.specificationVersion = specificationVersion; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getEndpointCount() { return endpointCount; }
    public void setEndpointCount(Integer endpointCount) { this.endpointCount = endpointCount; }
    public Integer getSchemaCount() { return schemaCount; }
    public void setSchemaCount(Integer schemaCount) { this.schemaCount = schemaCount; }
    public Integer getSecuritySchemeCount() { return securitySchemeCount; }
    public void setSecuritySchemeCount(Integer securitySchemeCount) { this.securitySchemeCount = securitySchemeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
