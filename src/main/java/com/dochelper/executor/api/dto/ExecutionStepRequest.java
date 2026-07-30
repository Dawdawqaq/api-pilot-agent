package com.dochelper.executor.api.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 单个受控 API 执行步骤。
 */
public record ExecutionStepRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 10) String method,
        @NotBlank @Size(max = 1000) String path,
        Map<String, String> pathVariables,
        Map<String, String> queryParams,
        Map<String, String> headers,
        JsonNode body,
        @Valid AuthenticationRequest authentication,
        @Valid List<VariableExtractorRequest> extractors,
        @Valid List<ResponseAssertionRequest> assertions,
        Boolean dangerousOperationConfirmed
) {
}
