package com.dochelper.executor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSONPath 响应变量提取规则。
 */
public record VariableExtractorRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$")
        String name,
        @NotBlank @Size(max = 500) String jsonPath
) {
}
