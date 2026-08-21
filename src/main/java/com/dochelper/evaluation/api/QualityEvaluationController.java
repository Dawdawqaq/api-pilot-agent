package com.dochelper.evaluation.api;

import java.util.List;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.evaluation.api.dto.RecordQualityEvaluationRequest;
import com.dochelper.evaluation.application.QualityEvaluationService;
import com.dochelper.evaluation.domain.QualityEvaluationRun;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 最终质量与安全评测结果接口。
 */
@RestController
@RequestMapping("/api/v1/quality-evaluations")
public class QualityEvaluationController {

    private final QualityEvaluationService service;
    private final BlockingOperationExecutor blockingExecutor;

    public QualityEvaluationController(
            QualityEvaluationService service,
            BlockingOperationExecutor blockingExecutor
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<QualityEvaluationRun>> record(
            @Valid @RequestBody RecordQualityEvaluationRequest request
    ) {
        return blockingExecutor.execute(() -> service.record(request)).map(ApiResponse::success);
    }

    @GetMapping
    public Mono<ApiResponse<List<QualityEvaluationRun>>> list(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return blockingExecutor.execute(() -> service.list(limit)).map(ApiResponse::success);
    }
}
