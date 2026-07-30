package com.dochelper.retrieval.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 检索评测运行数据库实体。
 */
@TableName("retrieval_evaluation_run")
public class EvaluationRunEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Integer topK;
    private Integer caseCount;
    private Integer hitCount;
    private BigDecimal recallAtK;
    private String detailsJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Integer getCaseCount() { return caseCount; }
    public void setCaseCount(Integer caseCount) { this.caseCount = caseCount; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public BigDecimal getRecallAtK() { return recallAtK; }
    public void setRecallAtK(BigDecimal recallAtK) { this.recallAtK = recallAtK; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
