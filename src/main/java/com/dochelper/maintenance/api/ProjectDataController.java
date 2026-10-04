package com.dochelper.maintenance.api;

import java.util.List;
import java.util.Map;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.maintenance.api.dto.CleanupRequest;
import com.dochelper.maintenance.api.vo.CleanupPreviewResponse;
import com.dochelper.maintenance.application.ProjectDataService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** 单人使用场景下的回收站与项目数据维护入口。 */
@RestController
@RequestMapping("/api/v1")
public class ProjectDataController {
    private final ProjectDataService service;
    private final BlockingOperationExecutor executor;
    public ProjectDataController(ProjectDataService service, BlockingOperationExecutor executor) {
        this.service = service; this.executor = executor;
    }
    @GetMapping("/projects/recycled") public Mono<ApiResponse<List<Map<String, Object>>>> recycled() {
        return executor.execute(service::recycled).map(ApiResponse::success);
    }
    @PostMapping("/projects/{projectId}/restoration") public Mono<ApiResponse<Boolean>> restore(@PathVariable Long projectId) {
        return executor.execute(() -> service.restore(projectId)).map(ApiResponse::success);
    }
    @GetMapping("/projects/{projectId}/data") public Mono<ApiResponse<Map<String, Long>>> counts(@PathVariable Long projectId) {
        return executor.execute(() -> service.counts(projectId)).map(ApiResponse::success);
    }
    @PostMapping("/projects/{projectId}/data/cleanup-preview") public Mono<ApiResponse<CleanupPreviewResponse>> preview(
            @PathVariable Long projectId, @RequestParam(defaultValue = "30") int retentionDays) {
        return executor.execute(() -> service.preview(projectId, retentionDays)).map(ApiResponse::success);
    }
    @PostMapping("/projects/{projectId}/data/cleanup") public Mono<ApiResponse<Map<String, Integer>>> clean(
            @PathVariable Long projectId, @Valid @RequestBody CleanupRequest request) {
        return executor.execute(() -> service.clean(projectId, request)).map(ApiResponse::success);
    }
}
