package com.dochelper.retrieval.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 检索评测用例数据库实体。
 */
@TableName("retrieval_evaluation_case")
public class EvaluationCaseEntity {

    @TableId
    private Long id;
    private Long projectId;
    private String caseName;
    private String queryText;
    private Long expectedDocumentId;
    private LocalDateTime createdAt;
    @TableLogic
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public Long getExpectedDocumentId() { return expectedDocumentId; }
    public void setExpectedDocumentId(Long expectedDocumentId) { this.expectedDocumentId = expectedDocumentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
