package com.dochelper.openapi.domain;

import java.util.List;

/**
 * 解析后的接口定义。
 */
public record ParsedEndpoint(
        String path,
        String httpMethod,
        String operationId,
        String summary,
        String description,
        String tagsJson,
        boolean deprecated,
        String requestBodyJson,
        String responsesJson,
        String securityJson,
        List<ParsedParameter> parameters
) {
}
