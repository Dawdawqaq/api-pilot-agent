package com.dochelper.report.api;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.report.api.vo.TestReportResponse;
import com.dochelper.report.api.vo.TestReportStepResponse;
import com.dochelper.report.application.TestReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * 测试报告列表与详情接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports")
public class TestReportController {

    private final TestReportService service;
    private final BlockingOperationExecutor blockingExecutor;
    private final ObjectMapper objectMapper;

    public TestReportController(
            TestReportService service,
            BlockingOperationExecutor blockingExecutor,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询项目测试报告。
     *
     * <p>GET /api/v1/projects/{projectId}/reports</p>
     */
    @GetMapping
    public Mono<ApiResponse<List<TestReportResponse>>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return blockingExecutor.execute(() -> service.list(projectId, limit).stream()
                        .map(report -> TestReportResponse.from(report, List.of(), objectMapper))
                        .toList())
                .map(ApiResponse::success);
    }

    /**
     * 查询包含脱敏请求响应的报告详情。
     *
     * <p>GET /api/v1/projects/{projectId}/reports/{reportId}</p>
     */
    @GetMapping("/{reportId}")
    public Mono<ApiResponse<TestReportResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long reportId
    ) {
        return blockingExecutor.execute(() -> TestReportResponse.from(
                        service.get(projectId, reportId),
                        service.steps(projectId, reportId).stream()
                                .map(step -> TestReportStepResponse.from(step, objectMapper))
                                .toList(),
                        objectMapper
                ))
                .map(ApiResponse::success);
    }

    /**
     * GET /api/v1/projects/{projectId}/reports/{reportId}/junit.xml。
     */
    @GetMapping(value = "/{reportId}/junit.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public Mono<ResponseEntity<String>> exportJUnit(
            @PathVariable Long projectId,
            @PathVariable Long reportId
    ) {
        return blockingExecutor.execute(() -> service.exportJUnit(projectId, reportId))
                .map(xml -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .body(xml));
    }
}
