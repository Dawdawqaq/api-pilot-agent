package com.dochelper.openapi.api.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.json.JsonValueReader;
import com.dochelper.openapi.domain.ApiEndpoint;

/**
 * OpenAPI 接口详情响应。
 */
public record ApiEndpointResponse(
        Long id,
        Long importId,
        String path,
        String httpMethod,
        String operationId,
        String summary,
        String description,
        JsonNode tags,
        boolean deprecated,
        JsonNode requestBody,
        JsonNode responses,
        JsonNode security,
        List<ParameterResponse> parameters
) {

    public static ApiEndpointResponse from(ApiEndpoint endpoint, ObjectMapper objectMapper) {
        JsonValueReader reader = new JsonValueReader(objectMapper);
        return new ApiEndpointResponse(
                endpoint.id(),
                endpoint.importId(),
                endpoint.path(),
                endpoint.httpMethod(),
                endpoint.operationId(),
                endpoint.summary(),
                endpoint.description(),
                reader.read(endpoint.tagsJson()),
                endpoint.deprecated(),
                reader.read(endpoint.requestBodyJson()),
                reader.read(endpoint.responsesJson()),
                reader.read(endpoint.securityJson()),
                endpoint.parameters().stream()
                        .map(parameter -> ParameterResponse.from(parameter, reader))
                        .toList()
        );
    }
}
