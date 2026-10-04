package com.dochelper.contract.application;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.contract.api.dto.ReplayFailureRequest;
import com.dochelper.contract.api.vo.FailureReplayResponse;
import com.dochelper.contract.api.vo.NegativeTestCaseResponse;
import com.dochelper.contract.domain.FailureReplaySample;
import com.dochelper.contract.domain.repository.ContractResultRepository;
import com.dochelper.executor.api.dto.ExecuteScenarioRequest;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.application.ScenarioExecutionService;
import com.dochelper.executor.domain.ScenarioExecutionResult;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.secret.application.SecretStore;
import org.springframework.stereotype.Service;

/**
 * 负向用例生成与失败样本回放服务。
 */
@Service
public class ContractTestService {

    private final OpenApiCatalogRepository catalogRepository;
    private final NegativeTestCaseGenerator negativeGenerator;
    private final ContractResultRepository resultRepository;
    private final SecretStore secretStore;
    private final ScenarioExecutionService executionService;
    private final ObjectMapper objectMapper;

    public ContractTestService(
            OpenApiCatalogRepository catalogRepository,
            NegativeTestCaseGenerator negativeGenerator,
            ContractResultRepository resultRepository,
            SecretStore secretStore,
            ScenarioExecutionService executionService,
            ObjectMapper objectMapper
    ) {
        this.catalogRepository = catalogRepository;
        this.negativeGenerator = negativeGenerator;
        this.resultRepository = resultRepository;
        this.secretStore = secretStore;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }

    public List<NegativeTestCaseResponse> generateNegativeCases(Long projectId, Long endpointId) {
        ApiEndpoint endpoint = catalogRepository.findEndpoint(projectId, endpointId)
                .orElseThrow(() -> new BusinessException(
                        com.dochelper.executor.exception.ExecutionErrorCode.ENDPOINT_NOT_IN_CATALOG
                ));
        return negativeGenerator.generate(endpoint);
    }

    public FailureReplayResponse getReplay(Long projectId, Long replayId) {
        FailureReplaySample sample = requireReplay(projectId, replayId);
        return toResponse(sample);
    }

    private FailureReplayResponse toResponse(FailureReplaySample sample) {
        try {
            return new FailureReplayResponse(
                    sample.id(), sample.executionId(), sample.stepIndex(),
                    sample.requestFingerprint(), objectMapper.readTree(sample.requestJsonRedacted()),
                    sample.errorSummary(), sample.createdAt()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("回放样本 JSON 数据损坏", exception);
        }
    }

    public com.dochelper.common.api.CursorPage<FailureReplayResponse> listReplays(Long projectId,
            Long executionId, com.dochelper.common.api.HistoryQuery query) {
        if (executionId != null && executionId <= 0) {
            throw new BusinessException(com.dochelper.common.exception.CommonErrorCode.INVALID_ARGUMENT,
                    "执行编号须为正整数");
        }
        var rows = resultRepository.findReplays(projectId, executionId, query).stream()
                .map(this::toResponse).toList();
        return com.dochelper.common.api.CursorPage.from(rows,
                resultRepository.countReplays(projectId, executionId, query.escapedQuery()),
                query.limit(), FailureReplayResponse::id);
    }

    public ScenarioExecutionResult replay(
            Long projectId,
            Long replayId,
            ReplayFailureRequest request
    ) {
        FailureReplaySample sample = requireReplay(projectId, replayId);
        String raw = secretStore.get(sample.requestSecretRef())
                .orElseThrow(() -> new BusinessException(
                        com.dochelper.executor.exception.ExecutionErrorCode.INVALID_STEP,
                        "回放样本的敏感运行数据已过期"
                ));
        try {
            ExecutionStepRequest step = objectMapper.readValue(raw, ExecutionStepRequest.class);
            return executionService.execute(
                    projectId,
                    new ExecuteScenarioRequest(request.environmentId(), Map.of(), List.of(step))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("回放样本运行数据损坏", exception);
        }
    }

    private FailureReplaySample requireReplay(Long projectId, Long replayId) {
        return resultRepository.findReplay(projectId, replayId)
                .orElseThrow(() -> new BusinessException(
                        com.dochelper.executor.exception.ExecutionErrorCode.EXECUTION_NOT_FOUND,
                        "失败回放样本不存在"
                ));
    }
}
