package com.dochelper.model.api;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.model.api.dto.UpdateLlmConfigurationRequest;
import com.dochelper.model.api.vo.LlmConfigurationResponse;
import com.dochelper.model.api.vo.LlmConnectionTestResponse;
import com.dochelper.model.application.LlmConfigurationService;
import com.dochelper.model.application.LlmConnectionTestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** 应用级 LLM 配置接口，与项目级资料出站规则分别管理。 */
@RestController
@RequestMapping("/api/v1/system/llm")
public class LlmConfigurationController {
    private final LlmConfigurationService configuration;
    private final LlmConnectionTestService connection;
    private final BlockingOperationExecutor executor;
    public LlmConfigurationController(LlmConfigurationService configuration, LlmConnectionTestService connection,
            BlockingOperationExecutor executor) {
        this.configuration = configuration;
        this.connection = connection;
        this.executor = executor;
    }

    @GetMapping
    public Mono<ApiResponse<LlmConfigurationResponse>> get() {
        return executor.execute(configuration::getConfiguration).map(ApiResponse::success);
    }

    @PutMapping
    public Mono<ApiResponse<LlmConfigurationResponse>> save(@Valid @RequestBody UpdateLlmConfigurationRequest request) {
        return executor.execute(() -> configuration.save(request)).map(ApiResponse::success);
    }

    @PostMapping("/test")
    public Mono<ApiResponse<LlmConnectionTestResponse>> test(@Valid @RequestBody UpdateLlmConfigurationRequest request) {
        return executor.execute(() -> connection.test(request)).map(ApiResponse::success);
    }
}
