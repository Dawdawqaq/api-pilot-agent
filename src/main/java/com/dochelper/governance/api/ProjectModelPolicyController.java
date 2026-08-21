package com.dochelper.governance.api;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.governance.api.dto.UpdateModelPolicyRequest;
import com.dochelper.governance.application.ProjectModelPolicyService;
import com.dochelper.governance.domain.ProjectModelPolicy;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 项目模型数据出站策略接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/model-policy")
public class ProjectModelPolicyController {

    private final ProjectModelPolicyService service;
    private final BlockingOperationExecutor blockingExecutor;

    public ProjectModelPolicyController(
            ProjectModelPolicyService service,
            BlockingOperationExecutor blockingExecutor
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
    }

    @GetMapping
    public Mono<ApiResponse<ProjectModelPolicy>> get(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> service.get(projectId)).map(ApiResponse::success);
    }

    @PutMapping
    public Mono<ApiResponse<ProjectModelPolicy>> update(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateModelPolicyRequest request
    ) {
        return blockingExecutor.execute(() -> service.update(projectId, request))
                .map(ApiResponse::success);
    }
}
