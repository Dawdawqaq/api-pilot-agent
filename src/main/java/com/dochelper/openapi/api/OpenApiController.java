package com.dochelper.openapi.api;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.openapi.api.vo.ApiEndpointResponse;
import com.dochelper.openapi.api.vo.OpenApiImportResponse;
import com.dochelper.openapi.application.OpenApiImportService;
import com.dochelper.openapi.config.OpenApiImportProperties;
import com.dochelper.openapi.exception.OpenApiErrorCode;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * OpenAPI 导入、版本与接口目录查询接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/openapi")
public class OpenApiController {

    private final OpenApiImportService service;
    private final BlockingOperationExecutor blockingExecutor;
    private final ObjectMapper objectMapper;
    private final OpenApiImportProperties properties;

    public OpenApiController(
            OpenApiImportService service,
            BlockingOperationExecutor blockingExecutor,
            ObjectMapper objectMapper,
            OpenApiImportProperties properties
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 上传并导入 JSON/YAML OpenAPI 文件。
     */
    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<OpenApiImportResponse>> importDocument(
            @PathVariable Long projectId,
            @RequestPart("file") FilePart file
    ) {
        return DataBufferUtils.join(file.content(), properties.maxFileSizeBytes())
                .map(buffer -> {
                    byte[] content = new byte[buffer.readableByteCount()];
                    buffer.read(content);
                    DataBufferUtils.release(buffer);
                    return content;
                })
                .flatMap(content -> blockingExecutor.execute(() -> service.importDocument(
                        projectId,
                        file.filename(),
                        file.headers().getContentType() == null
                                ? null
                                : file.headers().getContentType().toString(),
                        content
                )))
                .map(OpenApiImportResponse::from)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.error(new BusinessException(OpenApiErrorCode.EMPTY_FILE)))
                .onErrorMap(
                        DataBufferLimitException.class,
                        exception -> new BusinessException(OpenApiErrorCode.FILE_TOO_LARGE)
                );
    }

    @GetMapping("/imports")
    public Mono<ApiResponse<List<OpenApiImportResponse>>> listImports(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> service.listImports(projectId).stream()
                        .map(OpenApiImportResponse::from)
                        .toList())
                .map(ApiResponse::success);
    }

    @GetMapping("/imports/{importId}")
    public Mono<ApiResponse<OpenApiImportResponse>> getImport(
            @PathVariable Long projectId,
            @PathVariable Long importId
    ) {
        return blockingExecutor.execute(() ->
                        OpenApiImportResponse.from(service.getImport(projectId, importId)))
                .map(ApiResponse::success);
    }

    @PostMapping("/imports/{importId}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<OpenApiImportResponse>> retryImport(
            @PathVariable Long projectId,
            @PathVariable Long importId
    ) {
        return blockingExecutor.execute(() ->
                        OpenApiImportResponse.from(service.retryImport(projectId, importId)))
                .map(ApiResponse::success);
    }

    /**
     * 查询指定导入版本的接口；未传 importId 时查询项目当前生效版本。
     */
    @GetMapping("/endpoints")
    public Mono<ApiResponse<List<ApiEndpointResponse>>> listEndpoints(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long importId
    ) {
        return blockingExecutor.execute(() -> service.listEndpoints(projectId, importId).stream()
                        .map(endpoint -> ApiEndpointResponse.from(endpoint, objectMapper))
                        .toList())
                .map(ApiResponse::success);
    }

    @GetMapping("/endpoints/{endpointId}")
    public Mono<ApiResponse<ApiEndpointResponse>> getEndpoint(
            @PathVariable Long projectId,
            @PathVariable Long endpointId
    ) {
        return blockingExecutor.execute(() ->
                        ApiEndpointResponse.from(service.getEndpoint(projectId, endpointId), objectMapper))
                .map(ApiResponse::success);
    }
}
