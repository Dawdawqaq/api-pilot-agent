package com.dochelper.executor.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.executor.domain.AgentStepExecution;

/**
 * Agent 步骤级执行仓储。
 */
public interface AgentStepExecutionRepository {

    AgentStepExecution create(AgentStepExecution execution);

    void complete(AgentStepExecution execution);

    Optional<AgentStepExecution> findSucceeded(
            Long taskId,
            int stepIndex,
            String requestFingerprint
    );

    int nextAttempt(Long taskId, int stepIndex);

    List<AgentStepExecution> findByTaskId(Long taskId);
}
