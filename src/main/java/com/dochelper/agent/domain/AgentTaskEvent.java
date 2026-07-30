package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * Agent 任务事件。
 */
public record AgentTaskEvent(
        Long id,
        Long taskId,
        long sequenceNo,
        AgentEventType eventType,
        AgentTaskStatus state,
        String payloadJson,
        LocalDateTime createdAt
) {
}
