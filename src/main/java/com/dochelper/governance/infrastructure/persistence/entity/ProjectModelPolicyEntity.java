package com.dochelper.governance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 项目模型策略数据库实体。
 */
@TableName("project_model_policy")
public class ProjectModelPolicyEntity {
    @TableId(type = IdType.INPUT) private Long projectId;
    private Boolean externalModelAllowed;
    private String allowedProvider;
    private Boolean allowDocumentContent;
    private Boolean allowSchemaContent;
    private Integer promptCharacterBudget;
    private Integer endpointTopK;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Boolean getExternalModelAllowed() { return externalModelAllowed; }
    public void setExternalModelAllowed(Boolean externalModelAllowed) { this.externalModelAllowed = externalModelAllowed; }
    public String getAllowedProvider() { return allowedProvider; }
    public void setAllowedProvider(String allowedProvider) { this.allowedProvider = allowedProvider; }
    public Boolean getAllowDocumentContent() { return allowDocumentContent; }
    public void setAllowDocumentContent(Boolean allowDocumentContent) { this.allowDocumentContent = allowDocumentContent; }
    public Boolean getAllowSchemaContent() { return allowSchemaContent; }
    public void setAllowSchemaContent(Boolean allowSchemaContent) { this.allowSchemaContent = allowSchemaContent; }
    public Integer getPromptCharacterBudget() { return promptCharacterBudget; }
    public void setPromptCharacterBudget(Integer promptCharacterBudget) { this.promptCharacterBudget = promptCharacterBudget; }
    public Integer getEndpointTopK() { return endpointTopK; }
    public void setEndpointTopK(Integer endpointTopK) { this.endpointTopK = endpointTopK; }
}
