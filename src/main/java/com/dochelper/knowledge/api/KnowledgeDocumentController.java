package com.dochelper.knowledge.api;

import java.util.List;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.knowledge.api.vo.KnowledgeDocumentResponse;
import com.dochelper.knowledge.application.KnowledgeDocumentService;
import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.exception.KnowledgeErrorCode;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * 知识库文档上传、查询、重试和删除接口。
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService service;
    private final BlockingOperationExecutor blockingExecutor;
    private final KnowledgeProperties properties;

    public KnowledgeDocumentController(
            KnowledgeDocumentService service,
            BlockingOperationExecutor blockingExecutor,
            KnowledgeProperties properties
    ) {
        this.service = service;
        this.blockingExecutor = blockingExecutor;
        this.properties = properties;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<KnowledgeDocumentResponse>> upload(
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
                .flatMap(content -> blockingExecutor.execute(() -> service.upload(
                        projectId,
                        file.filename(),
                        file.headers().getContentType() == null
                                ? null
                                : file.headers().getContentType().toString(),
                        content
                )))
                .map(KnowledgeDocumentResponse::from)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.error(new BusinessException(KnowledgeErrorCode.EMPTY_FILE)))
                .onErrorMap(
                        DataBufferLimitException.class,
                        exception -> new BusinessException(KnowledgeErrorCode.FILE_TOO_LARGE)
                );
    }

    @GetMapping
    public Mono<ApiResponse<List<KnowledgeDocumentResponse>>> list(@PathVariable Long projectId) {
        return blockingExecutor.execute(() -> service.list(projectId).stream()
                        .map(KnowledgeDocumentResponse::from)
                        .toList())
                .map(ApiResponse::success);
    }

    @GetMapping("/{documentId}")
    public Mono<ApiResponse<KnowledgeDocumentResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    ) {
        return blockingExecutor.execute(() ->
                        KnowledgeDocumentResponse.from(service.get(projectId, documentId)))
                .map(ApiResponse::success);
    }

    @PostMapping("/{documentId}/retry")
    public Mono<ApiResponse<KnowledgeDocumentResponse>> retry(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    ) {
        return blockingExecutor.execute(() ->
                        KnowledgeDocumentResponse.from(service.retry(projectId, documentId)))
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{documentId}")
    public Mono<ApiResponse<Void>> delete(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    ) {
        return blockingExecutor.execute(() -> service.delete(projectId, documentId))
                .map(ignored -> ApiResponse.success(null));
    }
}
