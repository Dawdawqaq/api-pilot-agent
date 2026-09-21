package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * 一次待确认计划修订的原子持久化内容，明文运行数据仅通过引用关联。
 */
public record AgentPlanRevision(
        Long taskId,
        int expectedModificationCount,
        String planJsonRedacted,
        String contextJsonRedacted,
        int confirmationStepIndex,
        String confirmationJsonRedacted,
        String planHash,
        LocalDateTime expiresAt
) {
}
