package com.dochelper.executor.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecuteScenarioRequest;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.domain.AssertionResult;
import com.dochelper.executor.domain.ExecutionAudit;
import com.dochelper.executor.domain.ExecutionStatus;
import com.dochelper.executor.domain.ExecutionStepAudit;
import com.dochelper.executor.domain.ExecutionStepResult;
import com.dochelper.executor.domain.HttpExchangeResult;
import com.dochelper.executor.domain.ScenarioExecutionResult;
import com.dochelper.executor.domain.repository.ExecutionAuditRepository;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.project.domain.ProjectEnvironment;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import org.springframework.stereotype.Service;

/**
 * 编排多步骤受控 HTTP 调用、变量传递、断言和审计持久化。
 */
@Service
public class ScenarioExecutionService {

    private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final String MASK = "******";

    private final ApiProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final ExecutionAuditRepository auditRepository;
    private final ControlledHttpStepExecutor httpExecutor;
    private final JsonPathResponseProcessor responseProcessor;
    private final VariableTemplateRenderer templateRenderer;
    private final SensitiveDataSanitizer sanitizer;
    private final ExecutorProperties properties;
    private final ObjectMapper objectMapper;

    public ScenarioExecutionService(
            ApiProjectRepository projectRepository,
            ProjectEnvironmentRepository environmentRepository,
            ExecutionAuditRepository auditRepository,
            ControlledHttpStepExecutor httpExecutor,
            JsonPathResponseProcessor responseProcessor,
            VariableTemplateRenderer templateRenderer,
            SensitiveDataSanitizer sanitizer,
            ExecutorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.auditRepository = auditRepository;
        this.httpExecutor = httpExecutor;
        this.responseProcessor = responseProcessor;
        this.templateRenderer = templateRenderer;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ScenarioExecutionResult execute(Long projectId, ExecuteScenarioRequest request) {
        requireProject(projectId);
        ProjectEnvironment environment = requireEnvironment(projectId, request.environmentId());
        validateScenario(request);
        Long executionId = IdWorker.getId();
        LocalDateTime createdAt = LocalDateTime.now();
        auditRepository.create(new ExecutionAudit(
                executionId,
                projectId,
                environment.id(),
                ExecutionStatus.RUNNING,
                request.steps().size(),
                0,
                null,
                null,
                null,
                createdAt,
                null
        ));

        long startedAt = System.nanoTime();
        Map<String, Object> variables = new LinkedHashMap<>();
        if (request.initialVariables() != null) {
            variables.putAll(request.initialVariables());
        }
        List<ExecutionStepResult> results = new ArrayList<>();
        ExecutionStatus finalStatus = ExecutionStatus.SUCCEEDED;
        String errorCode = null;
        String errorMessage = null;

        for (int index = 0; index < request.steps().size(); index++) {
            ExecutionStepRequest step = request.steps().get(index);
            try {
                HttpExchangeResult exchange = httpExecutor.execute(environment, step, variables);
                List<AssertionResult> assertions = responseProcessor.assertResponse(
                        exchange.statusCode(),
                        exchange.responseBody(),
                        templateRenderer.renderAssertions(step.assertions(), variables)
                );
                Map<String, Object> extracted = responseProcessor.extract(
                        exchange.responseBody(),
                        step.extractors()
                );
                boolean passed = assertions.stream().allMatch(AssertionResult::passed);
                if (passed) {
                    variables.putAll(extracted);
                }
                ExecutionStepResult result = toResult(index, step, exchange, extracted, assertions, passed);
                results.add(result);
                saveAudit(executionId, result, exchange, extracted, assertions);
                if (!passed) {
                    finalStatus = ExecutionStatus.FAILED;
                    errorCode = "ASSERTION_FAILED";
                    errorMessage = "步骤断言失败：" + step.name();
                    break;
                }
            } catch (BusinessException exception) {
                finalStatus = ExecutionStatus.FAILED;
                errorCode = exception.getErrorCode().code();
                errorMessage = exception.getMessage();
                ExecutionStepResult failed = failedResult(index, step, exception.getMessage());
                results.add(failed);
                saveFailedAudit(executionId, failed);
                break;
            }
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        auditRepository.complete(
                executionId,
                finalStatus,
                results.size(),
                durationMs,
                errorCode,
                limit(errorMessage, 1000)
        );
        return new ScenarioExecutionResult(
                executionId,
                projectId,
                environment.id(),
                finalStatus,
                request.steps().size(),
                results.size(),
                durationMs,
                errorCode,
                errorMessage,
                List.copyOf(results),
                createdAt,
                LocalDateTime.now()
        );
    }

    public ScenarioExecutionResult get(Long projectId, Long executionId) {
        requireProject(projectId);
        ExecutionAudit audit = auditRepository.findByProjectAndId(projectId, executionId)
                .orElseThrow(() -> new BusinessException(ExecutionErrorCode.EXECUTION_NOT_FOUND));
        List<ExecutionStepResult> steps = auditRepository.findSteps(executionId).stream()
                .map(this::toHistoricalResult)
                .toList();
        return new ScenarioExecutionResult(
                audit.id(),
                audit.projectId(),
                audit.environmentId(),
                audit.status(),
                audit.stepCount(),
                audit.completedStepCount(),
                audit.durationMs() == null ? 0 : audit.durationMs(),
                audit.errorCode(),
                audit.errorMessage(),
                steps,
                audit.createdAt(),
                audit.completedAt()
        );
    }

    private ExecutionStepResult toResult(
            int index,
            ExecutionStepRequest step,
            HttpExchangeResult exchange,
            Map<String, Object> extracted,
            List<AssertionResult> assertions,
            boolean success
    ) {
        return new ExecutionStepResult(
                index,
                step.name().trim(),
                exchange.method(),
                sanitizer.sanitizeUrl(exchange.requestUrl()),
                exchange.statusCode(),
                sanitizer.sanitizeJson(exchange.responseBody()),
                sanitizer.sanitizeVariables(extracted),
                sanitizeAssertions(assertions),
                success,
                exchange.durationMs(),
                success ? null : "响应断言失败"
        );
    }

    private ExecutionStepResult failedResult(
            int index,
            ExecutionStepRequest step,
            String errorMessage
    ) {
        return new ExecutionStepResult(
                index,
                step.name().trim(),
                step.method().trim().toUpperCase(),
                null,
                0,
                null,
                Map.of(),
                List.of(),
                false,
                0,
                errorMessage
        );
    }

    private void saveAudit(
            Long executionId,
            ExecutionStepResult result,
            HttpExchangeResult exchange,
            Map<String, Object> extracted,
            List<AssertionResult> assertions
    ) {
        auditRepository.saveStep(new ExecutionStepAudit(
                IdWorker.getId(),
                executionId,
                result.stepIndex(),
                result.name(),
                result.method(),
                result.requestUrl(),
                toJson(sanitizer.sanitizeHeaders(exchange.requestHeaders())),
                toNullableJson(sanitizer.sanitizeJson(exchange.requestBody())),
                result.responseStatus(),
                toJson(sanitizer.sanitizeResponseHeaders(exchange.responseHeaders())),
                toNullableJson(sanitizer.sanitizeJson(exchange.responseBody())),
                toJson(extracted.keySet()),
                toJson(sanitizeAssertions(assertions)),
                result.success(),
                result.durationMs(),
                limit(result.errorMessage(), 1000),
                LocalDateTime.now()
        ));
    }

    private void saveFailedAudit(Long executionId, ExecutionStepResult result) {
        auditRepository.saveStep(new ExecutionStepAudit(
                IdWorker.getId(),
                executionId,
                result.stepIndex(),
                result.name(),
                result.method(),
                "请求未发送",
                "{}",
                null,
                null,
                null,
                null,
                "[]",
                "[]",
                false,
                result.durationMs(),
                limit(result.errorMessage(), 1000),
                LocalDateTime.now()
        ));
    }

    private ExecutionStepResult toHistoricalResult(ExecutionStepAudit step) {
        List<String> extractedNames = fromJson(
                step.extractedNamesJson(),
                new TypeReference<List<String>>() {
                }
        );
        Map<String, Object> extracted = new LinkedHashMap<>();
        extractedNames.forEach(name -> extracted.put(name, MASK));
        List<AssertionResult> assertions = fromJson(
                step.assertionsJson(),
                new TypeReference<List<AssertionResult>>() {
                }
        );
        return new ExecutionStepResult(
                step.stepIndex(),
                step.stepName(),
                step.httpMethod(),
                "请求未发送".equals(step.requestUrl()) ? null : step.requestUrl(),
                step.responseStatus() == null ? 0 : step.responseStatus(),
                parseNullableJson(step.responseBodyRedacted()),
                extracted,
                assertions,
                step.success(),
                step.durationMs(),
                step.errorMessage()
        );
    }

    private List<AssertionResult> sanitizeAssertions(List<AssertionResult> assertions) {
        return assertions.stream()
                .map(assertion -> {
                    if (!sanitizer.isSensitive(assertion.jsonPath())) {
                        return assertion;
                    }
                    return new AssertionResult(
                            assertion.type(),
                            assertion.jsonPath(),
                            assertion.passed(),
                            MASK,
                            MASK,
                            assertion.message()
                    );
                })
                .toList();
    }

    private void validateScenario(ExecuteScenarioRequest request) {
        if (request.steps().size() > properties.maxSteps()) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_STEP,
                    "执行步骤不能超过 " + properties.maxSteps()
            );
        }
        if (request.initialVariables() != null) {
            if (request.initialVariables().size() > 100
                    || request.initialVariables().keySet().stream()
                    .anyMatch(name -> name == null || !VARIABLE_NAME.matcher(name).matches())) {
                throw new BusinessException(
                        ExecutionErrorCode.INVALID_STEP,
                        "初始变量名称或数量不符合限制"
                );
            }
        }
    }

    private ProjectEnvironment requireEnvironment(Long projectId, Long environmentId) {
        if (environmentId != null) {
            return environmentRepository.findByIdAndProjectId(environmentId, projectId)
                    .orElseThrow(() -> new BusinessException(ProjectErrorCode.ENVIRONMENT_NOT_FOUND));
        }
        return environmentRepository.findByProjectId(projectId).stream()
                .filter(ProjectEnvironment::defaultEnvironment)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ExecutionErrorCode.ENVIRONMENT_REQUIRED));
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("执行审计 JSON 序列化失败", exception);
        }
    }

    private String toNullableJson(JsonNode value) {
        return value == null ? null : toJson(value);
    }

    private JsonNode parseNullableJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("执行审计 JSON 数据损坏", exception);
        }
    }

    private <T> T fromJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("执行审计 JSON 数据损坏", exception);
        }
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
