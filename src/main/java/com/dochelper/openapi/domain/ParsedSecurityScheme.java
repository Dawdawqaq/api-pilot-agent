package com.dochelper.openapi.domain;

/**
 * 解析后的安全方案。
 */
public record ParsedSecurityScheme(
        String name,
        String type,
        String httpScheme,
        String bearerFormat,
        String parameterName,
        String parameterLocation,
        String openidConnectUrl,
        String flowsJson
) {
}
