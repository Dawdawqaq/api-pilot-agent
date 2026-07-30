package com.dochelper.executor.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.executor.domain.AssertionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 响应断言配置。
 */
public record ResponseAssertionRequest(
        @NotNull AssertionType type,
        @Size(max = 500) String jsonPath,
        JsonNode expectedValue,
        @Size(max = 32) String expectedType
) {
}
