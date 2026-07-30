package com.dochelper.agent.api;

import java.util.List;

import com.dochelper.agent.api.dto.ConfirmationDecisionRequest;
import com.dochelper.agent.api.dto.CreateAgentTaskRequest;
import com.dochelper.agent.api.vo.AgentTaskEventResponse;
import com.dochelper.agent.api.vo.AgentTaskResponse;
import com.dochelper.agent.application.AgentTaskService;
import com.dochelper.agent.application.AgentEventStreamService;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

/**
 * Agent 任务、事件、确认与取消 REST API。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/agent-tasks")
public class AgentTaskController {

    private final AgentTaskService service;
    private final AgentEventStreamService streamService;
    private final BlockingOperationExecutor blockingExecutor;

    public AgentTaskController(
            AgentTaskService service,
            AgentEventStreamService streamService,
            BlockingOperationExecutor blockingExecutor
    ) {
        this.service = service;
        this.streamService = streamService;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * 创建并异步启动 Agent 任务。
     *
     * <p>POST /api/v1/projects/{projectId}/agent-tasks</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ApiResponse<AgentTaskResponse>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateAgentTaskRequest request
    ) {
        return blockingExecutor.execute(() -> service.create(projectId, request))
                .map(ApiResponse::success);
    }

    /**
     * 查询项目任务历史。
     *
     * <p>GET /api/v1/projects/{projectId}/agent-tasks</p>
     */
    @GetMapping
    public Mono<ApiResponse<List<AgentTaskResponse>>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return blockingExecutor.execute(() -> service.list(projectId, limit))
                .map(ApiResponse::success);
    }

    /**
     * 查询单个任务详情。
     *
     * <p>GET /api/v1/projects/{projectId}/agent-tasks/{taskId}</p>
     */
    @GetMapping("/{taskId}")
    public Mono<ApiResponse<AgentTaskResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long taskId
    ) {
        return blockingExecutor.execute(() -> service.get(projectId, taskId))
                .map(ApiResponse::success);
    }

    /**
     * 增量查询持久化任务事件。
     *
     * <p>GET /api/v1/projects/{projectId}/agent-tasks/{taskId}/events</p>
     */
    @GetMapping("/{taskId}/events")
    public Mono<ApiResponse<List<AgentTaskEventResponse>>> events(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return blockingExecutor.execute(() ->
                        service.events(projectId, taskId, after, limit))
                .map(ApiResponse::success);
    }

    /**
     * 以 SSE 推送持久化 Agent 事件，Last-Event-ID 只影响读取游标。
     *
     * <p>GET /api/v1/projects/{projectId}/agent-tasks/{taskId}/stream</p>
     */
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentTaskEventResponse>> stream(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") long after,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        long cursor = parseCursor(lastEventId, after);
        return streamService.stream(projectId, taskId, cursor)
                .map(event -> ServerSentEvent.<AgentTaskEventResponse>builder()
                        .id(String.valueOf(event.sequenceNo()))
                        .event("agent-event")
                        .retry(java.time.Duration.ofSeconds(1))
                        .data(event)
                        .build());
    }

    /**
     * 批准或拒绝待确认危险操作。
     *
     * <p>POST /api/v1/projects/{projectId}/agent-tasks/{taskId}/confirmation</p>
     */
    @PostMapping("/{taskId}/confirmation")
    public Mono<ApiResponse<AgentTaskResponse>> confirm(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody ConfirmationDecisionRequest request
    ) {
        return blockingExecutor.execute(() -> service.confirm(projectId, taskId, request))
                .map(ApiResponse::success);
    }

    /**
     * 协作式取消任务。
     *
     * <p>POST /api/v1/projects/{projectId}/agent-tasks/{taskId}/cancellation</p>
     */
    @PostMapping("/{taskId}/cancellation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ApiResponse<AgentTaskResponse>> cancel(
            @PathVariable Long projectId,
            @PathVariable Long taskId
    ) {
        return blockingExecutor.execute(() -> service.cancel(projectId, taskId))
                .map(ApiResponse::success);
    }

    private long parseCursor(String lastEventId, long fallback) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return Math.max(0, fallback);
        }
        try {
            return Math.max(0, Long.parseLong(lastEventId));
        } catch (NumberFormatException ignored) {
            return Math.max(0, fallback);
        }
    }
}
