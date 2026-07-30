package com.dochelper.openapi.api.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.common.json.JsonValueReader;
import com.dochelper.openapi.domain.ParsedParameter;

/**
 * OpenAPI 参数响应。
 */
public record ParameterResponse(
        String name,
        String location,
        boolean required,
        String description,
        JsonNode schema
) {

    static ParameterResponse from(ParsedParameter parameter, JsonValueReader jsonReader) {
        return new ParameterResponse(
                parameter.name(),
                parameter.location(),
                parameter.required(),
                parameter.description(),
                jsonReader.read(parameter.schemaJson())
        );
    }
}
