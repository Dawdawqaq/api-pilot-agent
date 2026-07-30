package com.dochelper.openapi.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * OpenAPI 安全方案数据库实体。
 */
@TableName("api_security_scheme")
public class ApiSecuritySchemeEntity {

    @TableId
    private Long id;
    private Long projectId;
    private Long importId;
    private String schemeName;
    private String schemeType;
    private String httpScheme;
    private String bearerFormat;
    private String parameterName;
    private String parameterLocation;
    private String openidConnectUrl;
    private String flowsJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getSchemeType() { return schemeType; }
    public void setSchemeType(String schemeType) { this.schemeType = schemeType; }
    public String getHttpScheme() { return httpScheme; }
    public void setHttpScheme(String httpScheme) { this.httpScheme = httpScheme; }
    public String getBearerFormat() { return bearerFormat; }
    public void setBearerFormat(String bearerFormat) { this.bearerFormat = bearerFormat; }
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }
    public String getParameterLocation() { return parameterLocation; }
    public void setParameterLocation(String parameterLocation) { this.parameterLocation = parameterLocation; }
    public String getOpenidConnectUrl() { return openidConnectUrl; }
    public void setOpenidConnectUrl(String openidConnectUrl) { this.openidConnectUrl = openidConnectUrl; }
    public String getFlowsJson() { return flowsJson; }
    public void setFlowsJson(String flowsJson) { this.flowsJson = flowsJson; }
}
