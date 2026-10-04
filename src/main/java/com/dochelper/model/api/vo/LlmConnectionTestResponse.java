package com.dochelper.model.api.vo;

/** 连接测试只返回状态，不回传供应商原始响应。 */
public record LlmConnectionTestResponse(boolean success, String message, String model, long durationMs) {
}
