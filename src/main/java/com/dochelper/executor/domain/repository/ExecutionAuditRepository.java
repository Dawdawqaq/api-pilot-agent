package com.dochelper.executor.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.executor.domain.ExecutionAudit;
import com.dochelper.executor.domain.ExecutionStepAudit;
import com.dochelper.executor.domain.ExecutionStatus;

/**
 * 场景执行审计仓储。
 */
public interface ExecutionAuditRepository {

    ExecutionAudit create(ExecutionAudit audit);

    void saveStep(ExecutionStepAudit step);

    void complete(
            Long executionId,
            ExecutionStatus status,
            int completedStepCount,
            long durationMs,
            String errorCode,
            String errorMessage
    );

    Optional<ExecutionAudit> findByProjectAndId(Long projectId, Long executionId);

    List<ExecutionStepAudit> findSteps(Long executionId);
}
