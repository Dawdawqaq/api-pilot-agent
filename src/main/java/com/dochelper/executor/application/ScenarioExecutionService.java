package com.dochelper.executor.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.contract.application.OpenApiContractValidator;
import com.dochelper.contract.domain.ContractOperationResult;
import com.dochelper.contract.domain.ContractValidationResult;
import com.dochelper.contract.domain.ContractViolation;
import com.dochelper.contract.domain.FailureReplaySample;
import com.dochelper.contract.domain.repository.ContractResultRepository;
import com.dochelper.executor.api.dto.ExecuteScenarioRequest;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.domain.AssertionResult;
import com.dochelper.executor.domain.AgentStepExecution;
import com.dochelper.executor.domain.ExecutionErrorCategory;
import com.dochelper.executor.domain.ExecutionAudit;
import com.dochelper.executor.domain.ExecutionStatus;
import com.dochelper.executor.domain.AssertionType;
import com.dochelper.executor.domain.ExecutionStepAudit;
import com.dochelper.executor.domain.ExecutionStepResult;
import com.dochelper.executor.domain.HttpExchangeResult;
import com.dochelper.executor.domain.ScenarioExecutionResult;
import com.dochelper.executor.domain.StepExecutionStatus;
import com.dochelper.executor.domain.ResolvedOperation;
import com.dochelper.executor.domain.repository.AgentStepExecutionRepository;
import com.dochelper.executor.domain.repository.ExecutionAuditRepository;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.project.domain.ProjectEnvironment;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import com.dochelper.secret.application.SecretStore;
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
    private final EndpointExecutionPolicy endpointExecutionPolicy;
    private final AgentStepExecutionRepository stepExecutionRepository;
    private final SecretStore secretStore;
    private final OpenApiContractValidator contractValidator;
    private final ContractResultRepository contractResultRepository;
    private final ExecutorProperties properties;
    private final StepRetryPolicy stepRetryPolicy;
    private final ObjectMapper objectMapper;

    public ScenarioExecutionService(
            ApiProjectRepository projectRepository,
            ProjectEnvironmentRepository environmentRepository,
            ExecutionAuditRepository auditRepository,
            ControlledHttpStepExecutor httpExecutor,
            JsonPathResponseProcessor responseProcessor,
            VariableTemplateRenderer templateRenderer,
            SensitiveDataSanitizer sanitizer,
            EndpointExecutionPolicy endpointExecutionPolicy,
            AgentStepExecutionRepository stepExecutionRepository,
            SecretStore secretStore,
            OpenApiContractValidator contractValidator,
            ContractResultRepository contractResultRepository,
            ExecutorProperties properties,
            StepRetryPolicy stepRetryPolicy,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.auditRepository = auditRepository;
        this.httpExecutor = httpExecutor;
        this.responseProcessor = responseProcessor;
        this.templateRenderer = templateRenderer;
        this.sanitizer = sanitizer;
        this.endpointExecutionPolicy = endpointExecutionPolicy;
        this.stepExecutionRepository = stepExecutionRepository;
        this.secretStore = secretStore;
        this.contractValidator = contractValidator;
        this.contractResultRepository = contractResultRepository;
        this.properties = properties;
        this.stepRetryPolicy = stepRetryPolicy;
        this.objectMapper = objectMapper;
    }

    public ScenarioExecutionResult execute(Long projectId, ExecuteScenarioRequest request) {
        return execute(null, projectId, request, Set.of());
    }

    /**
     * 使用服务端确认过的步骤下标执行场景，调用方不能从 HTTP 请求直接构造该凭据。
     */
    public ScenarioExecutionResult executeConfirmed(
            Long projectId,
            ExecuteScenarioRequest request,
            Set<Integer> confirmedStepIndexes
    ) {
        return execute(null, projectId, request, Set.copyOf(confirmedStepIndexes));
    }

    /**
     * 为 Agent 执行带任务标识的场景，以启用步骤级恢复和幂等审计。
     */
    public ScenarioExecutionResult executeForAgent(
            Long taskId,
            Long projectId,
            ExecuteScenarioRequest request,
            Set<Integer> confirmedStepIndexes
    ) {
        return execute(taskId, projectId, request, Set.copyOf(confirmedStepIndexes));
    }

    private ScenarioExecutionResult execute(
            Long taskId,
            Long projectId,
            ExecuteScenarioRequest request,
            Set<Integer> confirmedStepIndexes
    ) {
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
            String requestFingerprint = fingerprint(step);
            AgentStepExecution recovered = findRecovered(taskId, index, requestFingerprint);
            if (recovered != null) {
                restoreVariables(recovered, variables);
                ExecutionStepResult skipped = recoveredResult(index, step);
                results.add(skipped);
                saveRecoveredAudit(executionId, skipped);
                continue;
            }
            StepHttpOutcome httpOutcome = null;
            try {
                ResolvedOperation operation = endpointExecutionPolicy.validate(
                        projectId,
                        step,
                        confirmedStepIndexes.contains(index)
                );
                httpOutcome = executeHttpWithRetry(
                        taskId,
                        executionId,
                        index,
                        environment,
                        step,
                        variables,
                        requestFingerprint
                );
                HttpExchangeResult exchange = httpOutcome.exchange();
                List<AssertionResult> configuredAssertions = responseProcessor.assertResponse(
                        exchange.statusCode(),
                        exchange.responseBody(),
                        templateRenderer.renderAssertions(step.assertions(), variables)
                );
                Map<String, Object> extracted = responseProcessor.extract(
                        exchange.responseBody(),
                        step.extractors()
                );
                ContractValidationResult contract = contractValidator.validate(
                        projectId, operation, exchange.statusCode(), exchange.responseBody()
                );
                List<AssertionResult> assertions = new ArrayList<>(configuredAssertions);
                assertions.addAll(toContractAssertions(contract.violations()));
                boolean passed = assertions.stream().allMatch(AssertionResult::passed);
                if (passed) {
                    variables.putAll(extracted);
                }
                ExecutionStepResult result = toResult(index, step, exchange, extracted, assertions, passed);
                results.add(result);
                saveAudit(executionId, result, exchange, extracted, assertions);
                saveContractResult(
                        projectId, executionId, index, operation, exchange.statusCode(), contract
                );
                completeSuccessfulStep(
                        httpOutcome.stepExecution(), variables, exchange, extracted, passed
                );
                if (!passed) {
                    saveReplaySample(
                            projectId, executionId, index, step, requestFingerprint,
                            "断言或契约校验失败"
                    );
                    finalStatus = ExecutionStatus.FAILED;
                    errorCode = "ASSERTION_FAILED";
                    errorMessage = "步骤断言失败：" + step.name();
                    break;
                }
            } catch (BusinessException exception) {
                if (httpOutcome != null && httpOutcome.stepExecution() != null) {
                    completeFailedStep(httpOutcome.stepExecution(), exception, 0);
                }
                finalStatus = ExecutionStatus.FAILED;
                errorCode = exception.getErrorCode().code();
                errorMessage = exception.getMessage();
                ExecutionStepResult failed = failedResult(index, step, exception.getMessage());
                results.add(failed);
                saveFailedAudit(executionId, failed);
                saveReplaySample(
                        projectId, executionId, index, step, requestFingerprint,
                        sanitizer.sanitizeText(exception.getMessage())
                );
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

    private AgentStepExecution findRecovered(
            Long taskId,
            int stepIndex,
            String requestFingerprint
    ) {
        if (taskId == null) {
            return null;
        }
        return stepExecutionRepository.findSucceeded(taskId, stepIndex, requestFingerprint)
                .filter(execution -> execution.outputSecretRef() != null)
                .filter(execution -> secretStore.get(execution.outputSecretRef()).isPresent())
                .orElse(null);
    }

    private void restoreVariables(
            AgentStepExecution recovered,
            Map<String, Object> variables
    ) {
        String json = secretStore.get(recovered.outputSecretRef())
                .orElseThrow(() -> new BusinessException(
                        ExecutionErrorCode.INVALID_STEP,
                        "已完成步骤的运行时变量已过期，不能安全恢复"
                ));
        variables.putAll(fromJson(json, new TypeReference<Map<String, Object>>() { }));
    }

    private StepHttpOutcome executeHttpWithRetry(
            Long taskId,
            Long executionId,
            int stepIndex,
            ProjectEnvironment environment,
            ExecutionStepRequest step,
            Map<String, Object> variables,
            String requestFingerprint
    ) {
        int localAttempt = 0;
        while (true) {
            localAttempt++;
            AgentStepExecution running = createRunningStep(
                    taskId, executionId, stepIndex, step, variables, requestFingerprint
            );
            long startedAt = System.nanoTime();
            try {
                HttpExchangeResult exchange = httpExecutor.execute(environment, step, variables);
                return new StepHttpOutcome(exchange, running);
            } catch (BusinessException exception) {
                completeFailedStep(running, exception, elapsedMillis(startedAt));
                if (!stepRetryPolicy.shouldRetry(
                        step, exception.getErrorCode().code(), localAttempt
                )) {
                    throw exception;
                }
                stepRetryPolicy.pause(localAttempt);
            }
        }
    }

    private AgentStepExecution createRunningStep(
            Long taskId,
            Long executionId,
            int stepIndex,
            ExecutionStepRequest step,
            Map<String, Object> variables,
            String requestFingerprint
    ) {
        if (taskId == null) {
            return null;
        }
        String idempotencyKey = header(step, "Idempotency-Key");
        return stepExecutionRepository.create(new AgentStepExecution(
                IdWorker.getId(),
                taskId,
                executionId,
                stepIndex,
                stepExecutionRepository.nextAttempt(taskId, stepIndex),
                StepExecutionStatus.RUNNING,
                requestFingerprint,
                idempotencyKey == null ? null : sha256(idempotencyKey),
                toJson(variables.keySet()),
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null
        ));
    }

    private void completeSuccessfulStep(
            AgentStepExecution running,
            Map<String, Object> variables,
            HttpExchangeResult exchange,
            Map<String, Object> extracted,
            boolean passed
    ) {
        if (running == null) {
            return;
        }
        String outputReference = passed
                ? secretStore.put(
                        "agent-task:" + running.taskId() + ":step:" + running.stepIndex(),
                        toJson(variables),
                        Duration.ofHours(24)
                )
                : null;
        ExecutionErrorCategory category = passed
                ? null
                : exchange.statusCode() >= 500
                ? ExecutionErrorCategory.SERVER_5XX
                : ExecutionErrorCategory.ASSERTION;
        stepExecutionRepository.complete(new AgentStepExecution(
                running.id(), running.taskId(), running.executionId(), running.stepIndex(),
                running.attempt(), passed ? StepExecutionStatus.SUCCEEDED : StepExecutionStatus.FAILED,
                running.requestFingerprint(), running.idempotencyKeyHash(),
                running.inputVariableNamesJson(), outputReference, exchange.statusCode(), category,
                passed ? null : "ASSERTION_FAILED",
                passed ? null : "响应断言失败",
                exchange.durationMs(), running.createdAt(), LocalDateTime.now()
        ));
    }

    private void completeFailedStep(
            AgentStepExecution running,
            BusinessException exception,
            long durationMs
    ) {
        if (running == null) {
            return;
        }
        stepExecutionRepository.complete(new AgentStepExecution(
                running.id(), running.taskId(), running.executionId(), running.stepIndex(),
                running.attempt(), StepExecutionStatus.FAILED,
                running.requestFingerprint(), running.idempotencyKeyHash(),
                running.inputVariableNamesJson(), null, null,
                classify(exception.getErrorCode().code()), exception.getErrorCode().code(),
                limit(exception.getMessage(), 1000), durationMs,
                running.createdAt(), LocalDateTime.now()
        ));
    }

    private ExecutionErrorCategory classify(String code) {
        return switch (code) {
            case "EXECUTOR_502_001" -> ExecutionErrorCategory.CONNECTION;
            case "EXECUTOR_504_001" -> ExecutionErrorCategory.TIMEOUT;
            case "EXECUTOR_413_002" -> ExecutionErrorCategory.RESPONSE_TOO_LARGE;
            case "EXECUTOR_403_001", "EXECUTOR_403_002", "EXECUTOR_403_003",
                    "EXECUTOR_403_004", "EXECUTOR_409_001", "EXECUTOR_409_002"
                    -> ExecutionErrorCategory.SECURITY_POLICY;
            case "EXECUTOR_400_002", "EXECUTOR_422_001", "EXECUTOR_422_002"
                    -> ExecutionErrorCategory.INVALID_REQUEST;
            default -> ExecutionErrorCategory.UNKNOWN;
        };
    }

    private String header(ExecutionStepRequest step, String expectedName) {
        if (step.headers() == null) {
            return null;
        }
        return step.headers().entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && expectedName.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String fingerprint(ExecutionStepRequest step) {
        return sha256(toJson(step));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private ExecutionStepResult recoveredResult(int index, ExecutionStepRequest step) {
        return new ExecutionStepResult(
                index,
                step.name().trim(),
                step.method().trim().toUpperCase(java.util.Locale.ROOT),
                null,
                0,
                null,
                Map.of(),
                List.of(),
                true,
                0,
                null
        );
    }

    private void saveRecoveredAudit(Long executionId, ExecutionStepResult result) {
        auditRepository.saveStep(new ExecutionStepAudit(
                IdWorker.getId(), executionId, result.stepIndex(), result.name(), result.method(),
                "已从持久化步骤恢复", "{}", null, null, null, null,
                "[]", "[]", true, 0, null, LocalDateTime.now()
        ));
    }

    private List<AssertionResult> toContractAssertions(List<ContractViolation> violations) {
        return violations.stream()
                .map(violation -> new AssertionResult(
                        "DOCUMENTED_STATUS".equals(violation.rule())
                                ? AssertionType.STATUS_CODE
                                : AssertionType.FIELD_TYPE,
                        violation.jsonPath(),
                        false,
                        violation.rule(),
                        "VIOLATION",
                        violation.message()
                ))
                .toList();
    }

    private void saveContractResult(
            Long projectId,
            Long executionId,
            int stepIndex,
            ResolvedOperation operation,
            int responseStatus,
            ContractValidationResult result
    ) {
        contractResultRepository.saveOperationResult(new ContractOperationResult(
                IdWorker.getId(), projectId, executionId, stepIndex,
                operation.endpointId(), operation.operationId(), operation.method(),
                operation.pathTemplate(), responseStatus, result.totalRules(), result.coveredRules(),
                toJson(result.violations()), LocalDateTime.now()
        ));
    }

    private void saveReplaySample(
            Long projectId,
            Long executionId,
            int stepIndex,
            ExecutionStepRequest step,
            String requestFingerprint,
            String errorSummary
    ) {
        String secretReference = secretStore.put(
                "replay:" + projectId + ":" + executionId + ":" + stepIndex,
                toJson(step),
                Duration.ofDays(7)
        );
        JsonNode redacted = sanitizer.sanitizeJson(objectMapper.valueToTree(step));
        contractResultRepository.saveReplay(new FailureReplaySample(
                IdWorker.getId(), projectId, executionId, stepIndex, requestFingerprint,
                toJson(redacted), secretReference, errorSummary, LocalDateTime.now()
        ));
    }

    private record StepHttpOutcome(
            HttpExchangeResult exchange,
            AgentStepExecution stepExecution
    ) {
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
