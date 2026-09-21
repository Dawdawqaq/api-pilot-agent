package com.dochelper.executor.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.contract.application.OpenApiContractValidator;
import com.dochelper.contract.domain.repository.ContractResultRepository;
import com.dochelper.executor.api.dto.*;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.domain.HttpExchangeResult;
import com.dochelper.executor.domain.repository.*;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.project.domain.*;
import com.dochelper.project.domain.repository.*;
import com.dochelper.secret.application.InMemorySecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 从场景服务入口验证整链预检及写请求失败边界，确保策略实际接入执行流程。
 */
class ScenarioExecutionSafetyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ControlledHttpStepExecutor http;
    private EndpointExecutionPolicy endpoints;
    private JsonPathResponseProcessor processor;
    private ScenarioExecutionService service;
    private OpenApiContractValidator contracts;

    @BeforeEach
    void setUp() {
        var projects = mock(ApiProjectRepository.class);
        var environments = mock(ProjectEnvironmentRepository.class);
        when(projects.findById(1L)).thenReturn(Optional.of(mock(ApiProject.class)));
        when(environments.findByIdAndProjectId(2L, 1L)).thenReturn(Optional.of(mock(ProjectEnvironment.class)));
        var steps = mock(AgentStepExecutionRepository.class);
        when(steps.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(steps.nextAttempt(anyLong(), anyInt())).thenReturn(1);
        http = mock(ControlledHttpStepExecutor.class);
        endpoints = mock(EndpointExecutionPolicy.class);
        when(endpoints.validate(anyLong(), any(), anyBoolean())).thenReturn(mock(com.dochelper.executor.domain.ResolvedOperation.class));
        processor = mock(JsonPathResponseProcessor.class);
        var properties = new ExecutorProperties(Duration.ofSeconds(1), Duration.ofSeconds(1),
                1048576, 524288, 10, 2, Duration.ofMillis(1), "(?i).*(password|token|secret).*" );
        contracts = mock(OpenApiContractValidator.class);
        when(contracts.validate(anyLong(), any(), anyInt(), any())).thenReturn(
                new com.dochelper.contract.domain.ContractValidationResult(0, 0, List.of()));
        service = new ScenarioExecutionService(projects, environments, mock(ExecutionAuditRepository.class),
                http, processor, new VariableTemplateRenderer(mapper), new SensitiveDataSanitizer(properties),
                endpoints, steps, new InMemorySecretStore(), contracts,
                mock(ContractResultRepository.class), properties, new StepRetryPolicy(properties), mapper,
                new WriteExecutionPolicy());
    }

    @Test
    void shouldStopBeforeNextWriteAfterCancellationDuringRead() {
        var read = step("GET", "/ready", Map.of(), List.of());
        var write = step("POST", "/posts", Map.of(), List.of());
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(http.execute(any(), eq(read), anyMap())).thenAnswer(invocation -> {
            cancelled.set(true);
            return new HttpExchangeResult("http://localhost/ready", "GET", Map.of(), null,
                    200, Map.of(), mapper.createObjectNode(), "{}", 1);
        });
        var result = service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(read, write)), Set.of(1), () -> {
                    if (cancelled.get()) {
                        throw new BusinessException(com.dochelper.agent.exception.AgentErrorCode.TASK_CANCELLED);
                    }
                });
        assertThat(result.errorCode()).isEqualTo("AGENT_CANCELLED");
        verify(http, never()).execute(any(), eq(write), anyMap());
    }

    @Test
    void shouldCheckCancellationBeforeReadRetry() {
        var read = step("GET", "/ready", Map.of(), List.of());
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(http.execute(any(), eq(read), anyMap())).thenAnswer(invocation -> {
            cancelled.set(true);
            throw new BusinessException(ExecutionErrorCode.REQUEST_TIMEOUT);
        });
        var result = service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(read)), Set.of(), () -> {
                    if (cancelled.get()) {
                        throw new BusinessException(com.dochelper.agent.exception.AgentErrorCode.TASK_CANCELLED);
                    }
                });
        assertThat(result.errorCode()).isEqualTo("AGENT_CANCELLED");
        verify(http, times(1)).execute(any(), eq(read), anyMap());
    }

    @Test
    void shouldSendNothingWhenDeadlineAlreadyPassed() {
        assertThatThrownBy(() -> service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(step("POST", "/posts", Map.of(), List.of()))),
                Set.of(0), () -> { throw new BusinessException(com.dochelper.agent.exception.AgentErrorCode.TASK_TIMEOUT); }))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(http);
    }

    @Test
    void shouldNotSendEarlierWriteWhenLaterVariableIsMissing() {
        var write = step("POST", "/posts", Map.of(), List.of());
        var read = step("GET", "/posts/{{missingId}}", Map.of(), List.of());
        assertThatThrownBy(() -> service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(write, read)), Set.of(0)))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ExecutionErrorCode.VARIABLE_NOT_FOUND);
        verifyNoInteractions(http);
    }

    @Test
    void shouldNotSendEarlierWriteWhenLaterEndpointIsOutsideCatalog() {
        var write = step("POST", "/posts", Map.of(), List.of());
        var invalid = step("GET", "/unknown", Map.of(), List.of());
        when(endpoints.validate(1L, invalid, false)).thenThrow(new BusinessException(ExecutionErrorCode.ENDPOINT_NOT_IN_CATALOG));
        assertThatThrownBy(() -> service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(write, invalid)), Set.of(0)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(http);
    }

    @Test
    void shouldNotAllowExtractionToOverwriteExpectedInput() {
        var request = step("GET", "/posts", Map.of(), List.of(new VariableExtractorRequest("expectedTitle", "$.title")));
        assertThatThrownBy(() -> service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of("expectedTitle", "正确标题"), List.of(request)), Set.of()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(http);
    }

    @Test
    void shouldSendWriteOnlyOnceEvenWithIdempotencyHeaderWhenTimedOut() {
        var write = step("POST", "/posts", Map.of("Idempotency-Key", "fixture-key"), List.of());
        when(http.execute(any(), eq(write), anyMap())).thenThrow(new BusinessException(ExecutionErrorCode.REQUEST_TIMEOUT));
        var result = service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(write)), Set.of(0));
        assertThat(result.errorCode()).isEqualTo(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW.code());
        verify(http, times(1)).execute(any(), eq(write), anyMap());
    }

    @Test
    void shouldRequireReviewWhenWriteResponseCannotBeExtracted() {
        var write = step("POST", "/posts", Map.of(), List.of(new VariableExtractorRequest("id", "$.wrong")));
        when(http.execute(any(), eq(write), anyMap())).thenReturn(new HttpExchangeResult(
                "http://localhost/posts", "POST", Map.of(), null, 200, Map.of(), mapper.createObjectNode(), "{}", 1));
        when(processor.extract(any(), anyList())).thenThrow(new BusinessException(ExecutionErrorCode.INVALID_JSON_PATH));
        var result = service.executeForAgent(3L, 1L,
                new ExecuteScenarioRequest(2L, Map.of(), List.of(write)), Set.of(0));
        assertThat(result.errorCode()).isEqualTo(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW.code());
        verify(http, times(1)).execute(any(), eq(write), anyMap());
    }

    private ExecutionStepRequest step(String method, String path, Map<String, String> headers,
                                      List<VariableExtractorRequest> extractors) {
        return new ExecutionStepRequest("步骤", method, path, Map.of(), Map.of(), headers,
                null, null, extractors, List.of(), false);
    }
}
