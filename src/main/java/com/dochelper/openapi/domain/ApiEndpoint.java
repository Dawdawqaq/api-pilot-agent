package com.dochelper.openapi.domain;

import java.util.List;

/**
 * 已持久化的接口详情。
 */
public record ApiEndpoint(
        Long id,
        Long projectId,
        Long importId,
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
