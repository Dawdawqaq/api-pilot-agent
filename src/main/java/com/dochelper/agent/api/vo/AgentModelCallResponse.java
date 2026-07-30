package com.dochelper.agent.api.vo;

import java.time.LocalDateTime;

import com.dochelper.agent.domain.AgentModelCall;

/**
 * 不包含 Prompt 和密钥的模型调用指标响应。
 */
public record AgentModelCallResponse(
        Long id,
        String modelName,
        String status,
        int attempt,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static AgentModelCallResponse from(AgentModelCall call) {
        return new AgentModelCallResponse(
                call.id(),
                call.modelName(),
                call.status(),
                call.attempt(),
                call.promptTokens(),
                call.completionTokens(),
                call.totalTokens(),
                call.durationMs(),
                call.errorCode(),
                call.errorMessage(),
                call.createdAt(),
                call.completedAt()
        );
    }
}
