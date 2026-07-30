package com.dochelper.agent.api.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.agent.domain.AgentToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 工具调用响应。
 */
public record AgentToolCallResponse(
        Long id,
        int stepIndex,
        String toolName,
        String status,
        int attempt,
        JsonNode request,
        JsonNode response,
        Long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static AgentToolCallResponse from(AgentToolCall call, ObjectMapper objectMapper) {
        return new AgentToolCallResponse(
                call.id(),
                call.stepIndex(),
                call.toolName(),
                call.status().name(),
                call.attempt(),
                parse(objectMapper, call.requestJsonRedacted()),
                parse(objectMapper, call.responseJsonRedacted()),
                call.durationMs(),
                call.errorCode(),
                call.errorMessage(),
                call.createdAt(),
                call.completedAt()
        );
    }

    private static JsonNode parse(ObjectMapper objectMapper, String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Agent 工具审计 JSON 数据损坏", exception);
        }
    }
}
