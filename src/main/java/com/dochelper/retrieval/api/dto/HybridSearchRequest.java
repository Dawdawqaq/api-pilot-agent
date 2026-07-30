package com.dochelper.retrieval.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 混合检索请求。
 */
public record HybridSearchRequest(
        @NotBlank @Size(max = 1000) String query,
        @Min(1) @Max(20) Integer topK
) {
}
