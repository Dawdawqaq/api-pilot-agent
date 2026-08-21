package com.dochelper.contract.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.contract.domain.ContractOperationResult;
import com.dochelper.contract.domain.FailureReplaySample;
import com.dochelper.contract.domain.TestRunCoverage;

/**
 * 契约结果、覆盖明细与回放样本仓储。
 */
public interface ContractResultRepository {

    void saveOperationResult(ContractOperationResult result);

    List<ContractOperationResult> findByExecutionId(Long executionId);

    FailureReplaySample saveReplay(FailureReplaySample sample);

    Optional<FailureReplaySample> findReplay(Long projectId, Long replayId);

    void saveCoverage(TestRunCoverage coverage);
}
