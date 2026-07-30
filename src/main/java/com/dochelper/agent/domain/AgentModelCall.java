package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * 不保存 Prompt 内容和密钥的模型调用指标。
 */
public record AgentModelCall(
        Long id,
        Long taskId,
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
}
