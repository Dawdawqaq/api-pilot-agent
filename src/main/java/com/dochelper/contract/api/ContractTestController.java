package com.dochelper.contract.api;

import java.util.List;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.contract.api.dto.ReplayFailureRequest;
import com.dochelper.contract.api.vo.FailureReplayResponse;
import com.dochelper.contract.api.vo.NegativeTestCaseResponse;
import com.dochelper.contract.application.ContractTestService;
import com.dochelper.executor.api.vo.ScenarioExecutionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 契约负向用例与失败回放接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/contract-tests")
public class ContractTestController {

    private final ContractTestService service;
    private final BlockingOperationExecutor blockingExecutor;

    public ContractTestController(ContractTestService service, BlockingOperationExecutor blockingExecutor) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * GET /api/v1/projects/{projectId}/contract-tests/negative-cases?endpointId={id}。
     */
    @GetMapping("/negative-cases")
    public Mono<ApiResponse<List<NegativeTestCaseResponse>>> negativeCases(
            @PathVariable Long projectId,
            @RequestParam Long endpointId
    ) {
        return blockingExecutor.execute(() -> service.generateNegativeCases(projectId, endpointId))
                .map(ApiResponse::success);
    }

    @GetMapping("/replays/{replayId}")
    public Mono<ApiResponse<FailureReplayResponse>> getReplay(
            @PathVariable Long projectId,
            @PathVariable Long replayId
    ) {
        return blockingExecutor.execute(() -> service.getReplay(projectId, replayId))
                .map(ApiResponse::success);
    }

    /**
     * POST 回放只读请求；写请求仍会再次进入人工确认策略。
     */
    @PostMapping("/replays/{replayId}")
    public Mono<ApiResponse<ScenarioExecutionResponse>> replay(
            @PathVariable Long projectId,
            @PathVariable Long replayId,
            @Valid @RequestBody ReplayFailureRequest request
    ) {
        return blockingExecutor.execute(() -> ScenarioExecutionResponse.from(
                        service.replay(projectId, replayId, request)))
                .map(ApiResponse::success);
    }
}
