package com.dochelper.executor.api.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 多步骤受控 API 场景请求。
 */
public record ExecuteScenarioRequest(
        Long environmentId,
        Map<String, Object> initialVariables,
        @NotNull @NotEmpty @Size(max = 50) @Valid List<ExecutionStepRequest> steps
) {
}
