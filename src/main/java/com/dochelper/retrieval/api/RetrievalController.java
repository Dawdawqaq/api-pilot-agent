package com.dochelper.retrieval.api;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.retrieval.api.dto.CreateEvaluationCaseRequest;
import com.dochelper.retrieval.api.dto.HybridSearchRequest;
import com.dochelper.retrieval.api.dto.RunEvaluationRequest;
import com.dochelper.retrieval.api.vo.EvaluationCaseResponse;
import com.dochelper.retrieval.api.vo.EvaluationRunResponse;
import com.dochelper.retrieval.application.HybridRetrievalService;
import com.dochelper.retrieval.application.RetrievalEvaluationService;
import com.dochelper.retrieval.domain.RetrievalResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * 混合检索与 Recall@K 评测接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/retrieval")
public class RetrievalController {

    private final HybridRetrievalService retrievalService;
    private final RetrievalEvaluationService evaluationService;
    private final BlockingOperationExecutor blockingExecutor;
    private final ObjectMapper objectMapper;

    public RetrievalController(
            HybridRetrievalService retrievalService,
            RetrievalEvaluationService evaluationService,
            BlockingOperationExecutor blockingExecutor,
            ObjectMapper objectMapper
    ) {
        this.retrievalService = retrievalService;
        this.evaluationService = evaluationService;
        this.blockingExecutor = blockingExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/search")
    public Mono<ApiResponse<List<RetrievalResult>>> search(
            @PathVariable Long projectId,
            @Valid @RequestBody HybridSearchRequest request
    ) {
        int topK = request.topK() == null ? 5 : request.topK();
        return blockingExecutor.execute(() ->
                        retrievalService.search(projectId, request.query(), topK))
                .map(ApiResponse::success);
    }

    @PostMapping("/evaluation-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<EvaluationCaseResponse>> createCase(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEvaluationCaseRequest request
    ) {
        return blockingExecutor.execute(() -> EvaluationCaseResponse.from(
                        evaluationService.createCase(
                                projectId,
                                request.name(),
                                request.query(),
                                request.expectedDocumentId()
                        )
                ))
                .map(ApiResponse::success);
    }

    @GetMapping("/evaluation-cases")
    public Mono<ApiResponse<List<EvaluationCaseResponse>>> listCases(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> evaluationService.listCases(projectId).stream()
                        .map(EvaluationCaseResponse::from)
                        .toList())
                .map(ApiResponse::success);
    }

    @PostMapping("/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<EvaluationRunResponse>> runEvaluation(
            @PathVariable Long projectId,
            @Valid @RequestBody RunEvaluationRequest request
    ) {
        int topK = request.topK() == null ? 5 : request.topK();
        return blockingExecutor.execute(() -> EvaluationRunResponse.from(
                        evaluationService.run(projectId, topK),
                        objectMapper
                ))
                .map(ApiResponse::success);
    }

    @GetMapping("/evaluations")
    public Mono<ApiResponse<List<EvaluationRunResponse>>> listEvaluations(
            @PathVariable Long projectId
    ) {
        return blockingExecutor.execute(() -> evaluationService.listRuns(projectId).stream()
                        .map(run -> EvaluationRunResponse.from(run, objectMapper))
                        .toList())
                .map(ApiResponse::success);
    }
}
