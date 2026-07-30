package com.dochelper.executor.api;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.executor.api.dto.ExecuteScenarioRequest;
import com.dochelper.executor.api.vo.ScenarioExecutionResponse;
import com.dochelper.executor.application.ScenarioExecutionService;
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
 * 受控多步骤 API 场景执行与审计查询接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/executions")
public class ExecutionController {

    private final ScenarioExecutionService service;
    private final BlockingOperationExecutor blockingExecutor;

    public ExecutionController(
            ScenarioExecutionService service,
            BlockingOperationExecutor blockingExecutor
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<ScenarioExecutionResponse>> execute(
            @PathVariable Long projectId,
            @Valid @RequestBody ExecuteScenarioRequest request
    ) {
        return blockingExecutor.execute(() ->
                        ScenarioExecutionResponse.from(service.execute(projectId, request)))
                .map(ApiResponse::success);
    }

    @GetMapping("/{executionId}")
    public Mono<ApiResponse<ScenarioExecutionResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long executionId
    ) {
        return blockingExecutor.execute(() ->
                        ScenarioExecutionResponse.from(service.get(projectId, executionId)))
                .map(ApiResponse::success);
    }
}
