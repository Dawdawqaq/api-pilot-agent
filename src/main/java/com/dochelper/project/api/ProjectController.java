package com.dochelper.project.api;

import java.util.List;

import com.dochelper.common.security.CurrentUserAccessor;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.project.api.dto.CreateEnvironmentRequest;
import com.dochelper.project.api.dto.CreateProjectRequest;
import com.dochelper.project.api.dto.UpdateEnvironmentRequest;
import com.dochelper.project.api.dto.UpdateProjectRequest;
import com.dochelper.project.api.vo.EnvironmentResponse;
import com.dochelper.project.api.vo.ProjectResponse;
import com.dochelper.project.application.ProjectApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * 被测项目管理接口。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectApplicationService service;
    private final BlockingOperationExecutor blockingExecutor;
    private final CurrentUserAccessor currentUserAccessor;

    public ProjectController(
            ProjectApplicationService service,
            BlockingOperationExecutor blockingExecutor,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
        this.currentUserAccessor = currentUserAccessor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<ProjectResponse>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return currentUserAccessor.userId()
                .flatMap(userId -> blockingExecutor.execute(() -> ProjectResponse.from(
                        service.createProject(request, userId)
                )))
                .map(ApiResponse::success);
    }

    @GetMapping
    public Mono<ApiResponse<List<ProjectResponse>>> listProjects() {
        return currentUserAccessor.userId()
                .flatMap(userId -> blockingExecutor.execute(() -> service.listProjects(userId).stream()
                                .map(ProjectResponse::from)
                                .toList()))
                .map(ApiResponse::success);
    }

    @GetMapping("/{projectId}")
    public Mono<ApiResponse<ProjectResponse>> getProject(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> ProjectResponse.from(service.getProject(projectId)))
                .map(ApiResponse::success);
    }

    @PutMapping("/{projectId}")
    public Mono<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return blockingExecutor.execute(() -> ProjectResponse.from(service.updateProject(projectId, request)))
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{projectId}")
    public Mono<ApiResponse<Void>> deleteProject(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> service.deleteProject(projectId))
                .map(ignored -> ApiResponse.success(null));
    }

    @PostMapping("/{projectId}/environments")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<EnvironmentResponse>> createEnvironment(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEnvironmentRequest request
    ) {
        return blockingExecutor.execute(() -> EnvironmentResponse.from(
                        service.createEnvironment(projectId, request)
                ))
                .map(ApiResponse::success);
    }

    @GetMapping("/{projectId}/environments")
    public Mono<ApiResponse<List<EnvironmentResponse>>> listEnvironments(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> service.listEnvironments(projectId).stream()
                        .map(EnvironmentResponse::from)
                        .toList())
                .map(ApiResponse::success);
    }

    @PutMapping("/{projectId}/environments/{environmentId}")
    public Mono<ApiResponse<EnvironmentResponse>> updateEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long environmentId,
            @Valid @RequestBody UpdateEnvironmentRequest request
    ) {
        return blockingExecutor.execute(() -> EnvironmentResponse.from(
                        service.updateEnvironment(projectId, environmentId, request)
                ))
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{projectId}/environments/{environmentId}")
    public Mono<ApiResponse<Void>> deleteEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long environmentId
    ) {
        return blockingExecutor.execute(() -> service.deleteEnvironment(projectId, environmentId))
                .map(ignored -> ApiResponse.success(null));
    }
}
