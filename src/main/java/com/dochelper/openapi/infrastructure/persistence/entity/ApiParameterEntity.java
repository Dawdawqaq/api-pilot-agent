package com.dochelper.openapi.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * OpenAPI 参数数据库实体。
 */
@TableName("api_parameter")
public class ApiParameterEntity {

    @TableId
    private Long id;
    private Long endpointId;
    private String parameterName;
    private String location;
    private Boolean required;
    private String description;
    private String schemaJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEndpointId() { return endpointId; }
    public void setEndpointId(Long endpointId) { this.endpointId = endpointId; }
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
}
