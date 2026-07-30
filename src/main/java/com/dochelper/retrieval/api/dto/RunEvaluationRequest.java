package com.dochelper.retrieval.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 运行检索评测请求。
 */
public record RunEvaluationRequest(@Min(1) @Max(20) Integer topK) {
}
