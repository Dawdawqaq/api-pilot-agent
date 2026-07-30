package com.dochelper.executor.domain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单次受控 HTTP 调用结果。
 */
public record HttpExchangeResult(
        String requestUrl,
        String method,
        Map<String, String> requestHeaders,
        JsonNode requestBody,
        int statusCode,
        Map<String, List<String>> responseHeaders,
        JsonNode responseBody,
        String rawResponseBody,
        long durationMs
) {
}
