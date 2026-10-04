package com.dochelper.knowledge.api;

import java.util.Map;
import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.knowledge.api.dto.EmbeddingConfigurationRequest;
import com.dochelper.knowledge.api.vo.EmbeddingConfigurationResponse;
import com.dochelper.knowledge.application.KnowledgeIndexService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** 独立嵌入配置及受控重建接口。 */
@RestController
@RequestMapping("/api/v1/system/embedding")
public class EmbeddingConfigurationController {
    private final KnowledgeIndexService service;
    private final BlockingOperationExecutor executor;
    public EmbeddingConfigurationController(KnowledgeIndexService service, BlockingOperationExecutor executor) {
        this.service = service; this.executor = executor;
    }
    @GetMapping public Mono<ApiResponse<EmbeddingConfigurationResponse>> get() {
        return executor.execute(service::configuration).map(ApiResponse::success);
    }
    @PostMapping("/test") public Mono<ApiResponse<Map<String, Object>>> test(@Valid @RequestBody EmbeddingConfigurationRequest request) {
        return executor.execute(() -> service.test(request)).map(ApiResponse::success);
    }
    @PostMapping("/rebuild") public Mono<ApiResponse<EmbeddingConfigurationResponse>> rebuild(@Valid @RequestBody EmbeddingConfigurationRequest request) {
        return executor.execute(() -> service.rebuild(request)).map(ApiResponse::success);
    }
    @PostMapping("/cleanup") public Mono<ApiResponse<EmbeddingConfigurationResponse>> cleanup() {
        return executor.execute(service::cleanUnused).map(ApiResponse::success);
    }
}
