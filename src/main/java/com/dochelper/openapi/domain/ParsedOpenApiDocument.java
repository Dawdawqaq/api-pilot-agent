package com.dochelper.openapi.domain;

import java.util.List;

/**
 * OpenAPI 解析结果。
 */
public record ParsedOpenApiDocument(
        String specificationVersion,
        String title,
        String documentVersion,
        List<ParsedEndpoint> endpoints,
        List<ParsedSchema> schemas,
        List<ParsedSecurityScheme> securitySchemes,
        List<String> warnings
) {
}
