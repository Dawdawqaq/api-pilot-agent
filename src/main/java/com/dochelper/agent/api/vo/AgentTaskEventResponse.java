package com.dochelper.agent.api.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.domain.AgentTaskEvent;

/**
 * Agent 任务事件响应。
 */
public record AgentTaskEventResponse(
        long sequenceNo,
        String eventType,
        String state,
        JsonNode payload,
        LocalDateTime createdAt
) {

    public static AgentTaskEventResponse from(
            AgentTaskEvent event,
            ObjectMapper objectMapper
    ) {
        try {
            return new AgentTaskEventResponse(
                    event.sequenceNo(),
                    event.eventType().name(),
                    event.state().name(),
                    objectMapper.readTree(event.payloadJson()),
                    event.createdAt()
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Agent 事件 JSON 数据损坏", exception);
        }
    }
}
