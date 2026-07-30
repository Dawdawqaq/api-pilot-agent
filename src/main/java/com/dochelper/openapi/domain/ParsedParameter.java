package com.dochelper.openapi.domain;

/**
 * 解析后的接口参数。
 */
public record ParsedParameter(
        String name,
        String location,
        boolean required,
        String description,
        String schemaJson
) {
}
