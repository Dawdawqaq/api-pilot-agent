package com.dochelper.openapi.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * OpenAPI Schema 数据库实体。
 */
@TableName("api_schema_definition")
public class ApiSchemaEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Long importId;
    private String schemaName;
    private String schemaJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
}
