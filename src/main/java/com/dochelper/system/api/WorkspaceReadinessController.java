package com.dochelper.system.api;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.system.api.vo.WorkspaceReadinessResponse;
import com.dochelper.system.application.WorkspaceReadinessService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** 当前项目的提交前检查接口。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/readiness")
public class WorkspaceReadinessController {
    private final WorkspaceReadinessService service;
    private final BlockingOperationExecutor executor;
    public WorkspaceReadinessController(WorkspaceReadinessService service, BlockingOperationExecutor executor) {
        this.service = service;
        this.executor = executor;
    }
    @GetMapping
    public Mono<ApiResponse<WorkspaceReadinessResponse>> get(@PathVariable Long projectId,
            @RequestParam(required = false) Long environmentId) {
        return executor.execute(() -> service.check(projectId, environmentId)).map(ApiResponse::success);
    }
}
