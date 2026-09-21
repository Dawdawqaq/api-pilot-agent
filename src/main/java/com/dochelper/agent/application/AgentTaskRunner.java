package com.dochelper.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.AgentConfirmation;
import com.dochelper.agent.domain.AgentEventType;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskEvent;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.AgentToolCall;
import com.dochelper.agent.domain.AgentToolName;
import com.dochelper.agent.domain.ConfirmationStatus;
import com.dochelper.agent.domain.ToolCallStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.agent.tool.AgentTestReport;
import com.dochelper.agent.tool.AgentToolService;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecuteScenarioRequest;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.application.SensitiveDataSanitizer;
import com.dochelper.executor.domain.ScenarioExecutionResult;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.retrieval.domain.RetrievalResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 执行 Agent 的检索、规划、行动、观察、重规划和报告循环。
 */
@Component
public class AgentTaskRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTaskRunner.class);

    private final AgentTaskRepository repository;
    private final AgentToolService tools;
    private final OpenApiCatalogRepository openApiRepository;
    private final AgentPlanner planner;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentTaskStateMachine stateMachine;
    private final SensitiveDataSanitizer sanitizer;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AgentPlanRevisionPolicy revisionPolicy;
    private final OpenApiCandidateSelector candidateSelector;
    private final PlanningSchemaResolver schemaResolver;
    private final Set<Long> runningTasks = ConcurrentHashMap.newKeySet();
    private final String leaseOwner = "agent-" + UUID.randomUUID();

    public AgentTaskRunner(
            AgentTaskRepository repository,
            AgentToolService tools,
            OpenApiCatalogRepository openApiRepository,
            AgentPlanner planner,
            AgentRuntimeRegistry runtimeRegistry,
            AgentTaskStateMachine stateMachine,
            SensitiveDataSanitizer sanitizer,
            AgentProperties properties,
            ObjectMapper objectMapper,
            Validator validator,
            AgentPlanRevisionPolicy revisionPolicy,
            OpenApiCandidateSelector candidateSelector,
            PlanningSchemaResolver schemaResolver
    ) {
        this.repository = repository;
        this.tools = tools;
        this.openApiRepository = openApiRepository;
        this.planner = planner;
        this.runtimeRegistry = runtimeRegistry;
        this.stateMachine = stateMachine;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.revisionPolicy = revisionPolicy;
        this.candidateSelector = candidateSelector;
        this.schemaResolver = schemaResolver;
    }

    /**
     * 启动新任务的 ReAct 流程。
     */
    public void runNew(Long projectId, Long taskId) {
        if (!acquire(taskId)) {
            return;
        }
        try {
            AgentTask task = requireTask(projectId, taskId);
            checkRunnable(task);
            transition(task, AgentTaskStatus.RETRIEVING, AgentEventType.STATE_CHANGED, Map.of());
            task = requireTask(projectId, taskId);

            AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.require(task);
            boolean knowledgeEnabled = tools.businessKnowledgeEnabled();
            List<RetrievalResult> evidence = knowledgeEnabled
                    ? invokeTool(
                            task,
                            0,
                            AgentToolName.SEARCH_API_DOCUMENT,
                            Map.of("projectId", projectId, "query", runtime.goal(), "topK", 5),
                            () -> tools.searchApiDocument(projectId, runtime.goal(), 5)
                    )
                    : List.of();
            appendEvent(
                    taskId,
                    AgentTaskStatus.RETRIEVING,
                    AgentEventType.DOCUMENT_EVIDENCE_RETRIEVED,
                    Map.of(
                            "enabled", knowledgeEnabled,
                            "resultCount", evidence.size(),
                            "citations", evidence.stream().map(RetrievalResult::citation).toList()
                    )
            );

            task = requireTask(projectId, taskId);
            transition(task, AgentTaskStatus.PLANNING, AgentEventType.STATE_CHANGED, Map.of());
            List<OpenApiCandidateSelector.RankedEndpoint> rankedEndpoints = candidateSelector.select(
                    runtime.goal(),
                    openApiRepository.findEndpoints(projectId, null),
                    properties.defaultEndpointTopK()
            );
            List<ApiEndpoint> endpoints = rankedEndpoints.stream()
                    .map(OpenApiCandidateSelector.RankedEndpoint::endpoint)
                    .toList();
            appendEvent(
                    taskId,
                    AgentTaskStatus.PLANNING,
                    AgentEventType.OPENAPI_CANDIDATES_SELECTED,
                    Map.of(
                            "candidateCount", rankedEndpoints.size(),
                            "candidates", candidateEventPayload(rankedEndpoints)
                    )
            );
            int schemaCount = schemaResolver.collect(endpoints).size();
            appendEvent(
                    taskId,
                    AgentTaskStatus.PLANNING,
                    AgentEventType.SCHEMA_CONTEXT_READY,
                    Map.of("endpointCount", endpoints.size(), "schemaCount", schemaCount)
            );
            List<AgentPlanStep> plan = planner.plan(new AgentPlanningContext(
                    taskId,
                    projectId,
                    runtime.goal(),
                    evidence,
                    endpoints,
                    Set.copyOf(runtime.initialVariables().keySet()),
                    runtime.planHint()
            ));
            checkRunnable(requireTask(projectId, taskId));
            validatePlan(plan);
            String runtimeContextRef = runtimeRegistry.updatePlan(taskId, plan);
            repository.updatePlan(
                    taskId,
                    toSanitizedJson(plan),
                    toSanitizedJson(Map.of(
                            "initialVariableNames", runtime.initialVariables().keySet(),
                            "citations", planningCitations(rankedEndpoints, evidence),
                            "documentCitations", evidence.stream().map(RetrievalResult::citation).toList(),
                            "openApiCandidates", candidateEventPayload(rankedEndpoints),
                            "schemaCount", schemaCount,
                            "runtimeContextRef", runtimeContextRef
                    ))
            );
            appendEvent(
                    taskId,
                    AgentTaskStatus.PLANNING,
                    AgentEventType.PLAN_CREATED,
                    Map.of("stepCount", plan.size(), "plan", sanitizedValue(plan))
            );

            AgentPlanStep dangerous = plan.stream().filter(AgentPlanStep::dangerous).findFirst()
                    .orElse(null);
            if (dangerous != null) {
                waitForConfirmation(projectId, taskId, dangerous, plan);
                return;
            }
            executePlan(projectId, taskId, plan);
        } catch (Exception exception) {
            failSafely(projectId, taskId, exception);
        } finally {
            release(taskId);
        }
    }

    /**
     * 人工批准后继续执行已规划任务。
     */
    public void resumeApproved(Long projectId, Long taskId) {
        if (!acquire(taskId)) {
            return;
        }
        try {
            AgentTask task = requireTask(projectId, taskId);
            if (task.status() != AgentTaskStatus.WAITING_CONFIRMATION) {
                throw new BusinessException(AgentErrorCode.INVALID_STATE);
            }
            AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.require(task);
            executePlan(projectId, taskId, runtime.plan());
        } catch (Exception exception) {
            failSafely(projectId, taskId, exception);
        } finally {
            release(taskId);
        }
    }

    /**
     * 在应用重启后从已恢复的 SecretStore 上下文继续未完成计划。
     */
    public void resumeRecovered(Long projectId, Long taskId) {
        if (!acquire(taskId)) {
            return;
        }
        try {
            repository.interruptRunningToolCalls(taskId, LocalDateTime.now());
            AgentTask task = requireTask(projectId, taskId);
            checkRunnable(task);
            if (task.status() == AgentTaskStatus.WAITING_CONFIRMATION) {
                AgentConfirmation confirmation = repository.findConfirmation(taskId, task.currentStep())
                        .orElseThrow(() -> new BusinessException(AgentErrorCode.CONFIRMATION_NOT_FOUND));
                if (confirmation.status() == ConfirmationStatus.PENDING) {
                    if (!LocalDateTime.now().isBefore(confirmation.expiresAt())) {
                        repository.decideConfirmation(confirmation.id(), ConfirmationStatus.PENDING,
                                ConfirmationStatus.EXPIRED, "确认等待超时", null, LocalDateTime.now());
                        throw new BusinessException(AgentErrorCode.CONFIRMATION_EXPIRED);
                    }
                    return;
                }
            }
            AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.require(task);
            if (runtime.plan().isEmpty()) {
                throw new BusinessException(AgentErrorCode.RECOVERY_UNAVAILABLE);
            }
            repository.updateProgress(
                    taskId, AgentTaskStatus.REPLANNING, task.currentStep(),
                    task.toolCallCount(), task.replanCount(), task.resultSummary(),
                    null, null, task.startedAt(), null
            );
            appendEvent(
                    taskId, AgentTaskStatus.REPLANNING, AgentEventType.REPLAN_STARTED,
                    Map.of("reason", "应用重启后从步骤持久化记录恢复")
            );
            executePlan(projectId, taskId, runtime.plan());
        } catch (Exception exception) {
            failSafely(projectId, taskId, exception);
        } finally {
            release(taskId);
        }
    }

    /**
     * 周期续租，避免长模型调用或 HTTP 请求期间被其他实例重复领取。
     */
    @Scheduled(fixedDelay = 10000)
    public void renewActiveLeases() {
        LocalDateTime leaseUntil = LocalDateTime.now().plus(properties.leaseDuration());
        runningTasks.forEach(taskId -> repository.renewLease(taskId, leaseOwner, leaseUntil));
    }

    private boolean acquire(Long taskId) {
        if (!runningTasks.add(taskId)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean acquired = repository.tryAcquireLease(
                taskId, leaseOwner, now, now.plus(properties.leaseDuration())
        );
        if (!acquired) {
            runningTasks.remove(taskId);
        }
        return acquired;
    }

    private void release(Long taskId) {
        repository.releaseLease(taskId, leaseOwner);
        runningTasks.remove(taskId);
    }

    private void executePlan(Long projectId, Long taskId, List<AgentPlanStep> plan) {
        AgentTask task = requireTask(projectId, taskId);
        checkRunnable(task);
        Set<Integer> confirmedStepIndexes = plan.stream()
                .filter(AgentPlanStep::dangerous)
                .map(AgentPlanStep::index)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!confirmedStepIndexes.isEmpty()) {
            verifyApprovedPlan(task, plan);
        }
        transition(task, AgentTaskStatus.EXECUTING, AgentEventType.STATE_CHANGED, Map.of());
        AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.find(taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.INVALID_STATE));
        List<ExecutionStepRequest> approvedSteps = plan.stream()
                .map(AgentPlanStep::request)
                .toList();
        ExecuteScenarioRequest request = new ExecuteScenarioRequest(
                task.environmentId(),
                runtime.initialVariables(),
                approvedSteps
        );

        int replanCount = task.replanCount();
        checkRunnable(requireTask(projectId, taskId));
        task = requireTask(projectId, taskId);
        ScenarioExecutionResult result = invokeTool(
                task,
                task.currentStep(),
                AgentToolName.EXECUTE_HTTP_REQUEST,
                request,
                () -> tools.executeConfirmedHttpRequest(
                        taskId, projectId, request, confirmedStepIndexes,
                        () -> checkRunnable(requireTask(projectId, taskId))
                )
        );
        checkRunnable(requireTask(projectId, taskId));
        transition(
                requireTask(projectId, taskId),
                AgentTaskStatus.OBSERVING,
                AgentEventType.STATE_CHANGED,
                Map.of(
                        "executionId", result.id(),
                        "executionStatus", result.status().name(),
                        "completedSteps", result.completedStepCount()
                )
        );
        if (result.status() != com.dochelper.executor.domain.ExecutionStatus.SUCCEEDED) {
            if (ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW.code().equals(result.errorCode())) {
                throw new BusinessException(
                        ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW,
                        safeMessage(result.errorMessage())
                );
            }
            if (!replannable(result.errorCode()) || replanCount >= properties.maxReplans()) {
                throw new BusinessException(
                        AgentErrorCode.INVALID_PLAN,
                        "执行计划失败：" + safeMessage(result.errorMessage())
                );
            }
            List<AgentPlanStep> revisedPlan = replan(
                    projectId, taskId, plan, result, replanCount + 1, runtime
            );
            AgentPlanStep risky = revisedPlan.stream().filter(AgentPlanStep::dangerous)
                    .findFirst().orElse(null);
            if (risky != null) {
                waitForConfirmation(projectId, taskId, risky, revisedPlan);
                return;
            }
            executePlan(projectId, taskId, revisedPlan);
            return;
        }

        AgentTask reportingTask = requireTask(projectId, taskId);
        stateMachine.assertTransition(reportingTask.status(), AgentTaskStatus.REPORTING);
        repository.updateProgress(
                taskId,
                AgentTaskStatus.REPORTING,
                plan.size(),
                reportingTask.toolCallCount(),
                replanCount,
                null,
                null,
                null,
                reportingTask.startedAt(),
                null
        );
        appendEvent(
                taskId,
                AgentTaskStatus.REPORTING,
                AgentEventType.STATE_CHANGED,
                Map.of("executionId", result.id())
        );
        AgentTestReport report = invokeTool(
                requireTask(projectId, taskId),
                plan.size(),
                AgentToolName.GENERATE_TEST_REPORT,
                Map.of("projectId", projectId, "taskId", taskId),
                () -> tools.generateTestReport(projectId, taskId)
        );
        completeSuccess(projectId, taskId, report);
    }

    private List<AgentPlanStep> replan(
            Long projectId,
            Long taskId,
            List<AgentPlanStep> originalPlan,
            ScenarioExecutionResult result,
            int replanCount,
            AgentRuntimeRegistry.RuntimeContext runtime
    ) {
        AgentTask current = requireTask(projectId, taskId);
        stateMachine.assertTransition(current.status(), AgentTaskStatus.REPLANNING);
        int completedCount = (int) result.steps().stream()
                .takeWhile(com.dochelper.executor.domain.ExecutionStepResult::success)
                .count();
        repository.updateProgress(
                taskId, AgentTaskStatus.REPLANNING, completedCount,
                current.toolCallCount(), replanCount, null, null, null,
                current.startedAt(), null
        );
        appendEvent(
                taskId, AgentTaskStatus.REPLANNING, AgentEventType.REPLAN_STARTED,
                Map.of(
                        "replanCount", replanCount,
                        "reason", safeMessage(result.errorMessage()),
                        "failureCode", result.errorCode() == null ? "UNKNOWN" : result.errorCode()
                )
        );
        Set<String> variableNames = new java.util.LinkedHashSet<>(runtime.initialVariables().keySet());
        result.steps().stream().filter(com.dochelper.executor.domain.ExecutionStepResult::success)
                .forEach(step -> variableNames.addAll(step.extractedVariables().keySet()));
        List<AgentPlanStep> revised = planner.replan(new AgentReplanContext(
                taskId, projectId, runtime.goal(), originalPlan,
                result.steps().stream().limit(completedCount).toList(),
                Set.copyOf(variableNames), result.errorCode(), safeMessage(result.errorMessage()),
                candidateSelector.select(
                                runtime.goal(), openApiRepository.findEndpoints(projectId, null),
                                properties.defaultEndpointTopK()
                        ).stream()
                        .map(OpenApiCandidateSelector.RankedEndpoint::endpoint)
                        .toList()
        ));
        checkRunnable(requireTask(projectId, taskId));
        validatePlan(revised);
        revisionPolicy.validate(originalPlan, revised, completedCount);
        String runtimeContextRef = runtimeRegistry.updatePlan(taskId, revised);
        repository.updatePlan(
                taskId,
                toSanitizedJson(revised),
                toSanitizedJson(Map.of(
                        "replanCount", replanCount,
                        "failureCode", result.errorCode() == null ? "UNKNOWN" : result.errorCode(),
                        "lockedStepCount", completedCount,
                        "runtimeContextRef", runtimeContextRef
                ))
        );
        appendEvent(
                taskId, AgentTaskStatus.REPLANNING, AgentEventType.PLAN_REVISED,
                Map.of(
                        "replanCount", replanCount,
                        "lockedStepCount", completedCount,
                        "changedStepIndexes", changedStepIndexes(originalPlan, revised)
                )
        );
        return revised;
    }

    private List<Integer> changedStepIndexes(
            List<AgentPlanStep> original,
            List<AgentPlanStep> revised
    ) {
        List<Integer> changed = new ArrayList<>();
        for (int index = 0; index < Math.min(original.size(), revised.size()); index++) {
            if (!original.get(index).equals(revised.get(index))) {
                changed.add(index);
            }
        }
        return List.copyOf(changed);
    }

    private List<Map<String, Object>> candidateEventPayload(
            List<OpenApiCandidateSelector.RankedEndpoint> candidates
    ) {
        return candidates.stream().map(candidate -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("endpointId", candidate.endpoint().id());
            value.put("method", candidate.endpoint().httpMethod());
            value.put("path", candidate.endpoint().path());
            value.put("summary", candidate.endpoint().summary());
            value.put("score", candidate.score());
            value.put("matchedTerms", candidate.matchedTerms());
            value.put("reasons", candidate.reasons());
            return value;
        }).toList();
    }

    private List<String> planningCitations(
            List<OpenApiCandidateSelector.RankedEndpoint> candidates,
            List<RetrievalResult> evidence
    ) {
        List<String> citations = new ArrayList<>();
        candidates.forEach(candidate -> citations.add(
                "OpenAPI " + candidate.endpoint().httpMethod() + " " + candidate.endpoint().path()
                        + " · " + safeMessage(candidate.endpoint().summary())
                        + " · score=" + candidate.score()
        ));
        evidence.stream().map(RetrievalResult::citation).forEach(citations::add);
        return List.copyOf(citations);
    }

    private void waitForConfirmation(
            Long projectId,
            Long taskId,
            AgentPlanStep dangerous,
            List<AgentPlanStep> plan
    ) {
        AgentTask task = requireTask(projectId, taskId);
        LocalDateTime now = LocalDateTime.now();
        checkRunnable(task);
        LocalDateTime expiresAt = now.plus(properties.confirmationTimeout());
        if (expiresAt.isAfter(task.deadlineAt())) {
            expiresAt = task.deadlineAt();
        }
        repository.createConfirmation(new AgentConfirmation(
                IdWorker.getId(),
                taskId,
                dangerous.index(),
                ConfirmationStatus.PENDING,
                toSanitizedJson(plan.stream().filter(AgentPlanStep::dangerous).toList()),
                sha256(toJson(plan)),
                null,
                null,
                expiresAt,
                now,
                null
        ));
        stateMachine.assertTransition(task.status(), AgentTaskStatus.WAITING_CONFIRMATION);
        repository.updateProgress(
                taskId,
                AgentTaskStatus.WAITING_CONFIRMATION,
                dangerous.index(),
                task.toolCallCount(),
                task.replanCount(),
                null,
                null,
                null,
                task.startedAt(),
                null
        );
        appendEvent(
                taskId,
                AgentTaskStatus.WAITING_CONFIRMATION,
                AgentEventType.CONFIRMATION_REQUIRED,
                Map.of(
                        "stepIndex", dangerous.index(),
                        "method", dangerous.request().method(),
                        "path", dangerous.request().path(),
                        "expiresAt", expiresAt.toString()
                )
        );
    }

    private void verifyApprovedPlan(AgentTask task, List<AgentPlanStep> plan) {
        AgentPlanStep firstRisky = plan.stream().filter(AgentPlanStep::dangerous).findFirst()
                .orElseThrow(() -> new BusinessException(AgentErrorCode.INVALID_PLAN));
        AgentConfirmation confirmation = repository.findConfirmation(task.id(), firstRisky.index())
                .orElseThrow(() -> new BusinessException(AgentErrorCode.CONFIRMATION_NOT_FOUND));
        if (confirmation.status() != ConfirmationStatus.APPROVED
                || LocalDateTime.now().isAfter(confirmation.expiresAt())
                || !sha256(toJson(plan)).equals(confirmation.planHash())) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_STATE,
                    "确认记录与当前计划不一致，拒绝执行写操作"
            );
        }
    }

    private <T> T invokeTool(
            AgentTask task,
            int stepIndex,
            AgentToolName toolName,
            Object request,
            Supplier<T> action
    ) {
        checkRunnable(task);
        List<AgentToolCall> existingCalls = repository.findToolCalls(task.id());
        if (existingCalls.size() >= properties.maxToolCalls()) {
            throw new BusinessException(AgentErrorCode.TOOL_CALL_LIMIT);
        }
        String requestJson = toJson(request);
        String callKey = sha256(toolName.value() + ":" + requestJson);
        boolean alreadySucceeded = existingCalls.stream().anyMatch(call ->
                call.callKey().equals(callKey) && call.status() == ToolCallStatus.SUCCEEDED
        );
        if (alreadySucceeded) {
            throw new BusinessException(AgentErrorCode.DUPLICATE_TOOL_CALL);
        }
        int attempt = (int) existingCalls.stream()
                .filter(call -> call.callKey().equals(callKey))
                .count() + 1;
        if (attempt > properties.maxToolRetries() + 1) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_STATE,
                    "工具调用重试次数达到上限"
            );
        }

        Long callId = IdWorker.getId();
        LocalDateTime startedAt = LocalDateTime.now();
        AgentToolCall running = new AgentToolCall(
                callId,
                task.id(),
                stepIndex,
                toolName.value(),
                callKey,
                toSanitizedJson(request),
                null,
                ToolCallStatus.RUNNING,
                attempt,
                null,
                null,
                null,
                startedAt,
                null
        );
        repository.createToolCall(running);
        appendEvent(
                task.id(),
                task.status(),
                attempt == 1 ? AgentEventType.TOOL_STARTED : AgentEventType.TOOL_RETRIED,
                Map.of("toolCallId", callId, "toolName", toolName.value(), "attempt", attempt)
        );
        long startNanos = System.nanoTime();
        try {
            T response = action.get();
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            boolean successful = !(response instanceof ScenarioExecutionResult executionResult)
                    || executionResult.status()
                    == com.dochelper.executor.domain.ExecutionStatus.SUCCEEDED;
            repository.completeToolCall(new AgentToolCall(
                    callId,
                    task.id(),
                    stepIndex,
                    toolName.value(),
                    callKey,
                    running.requestJsonRedacted(),
                    toSanitizedJson(response),
                    successful ? ToolCallStatus.SUCCEEDED : ToolCallStatus.FAILED,
                    attempt,
                    durationMs,
                    null,
                    null,
                    startedAt,
                    LocalDateTime.now()
            ));
            updateToolCount(task);
            appendEvent(
                    task.id(),
                    task.status(),
                    successful ? AgentEventType.TOOL_COMPLETED : AgentEventType.TOOL_FAILED,
                    Map.of(
                            "toolCallId", callId,
                            "toolName", toolName.value(),
                            "durationMs", durationMs
                    )
            );
            return response;
        } catch (RuntimeException exception) {
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            String errorCode = exception instanceof BusinessException business
                    ? business.getErrorCode().code()
                    : "AGENT_TOOL_ERROR";
            repository.completeToolCall(new AgentToolCall(
                    callId,
                    task.id(),
                    stepIndex,
                    toolName.value(),
                    callKey,
                    running.requestJsonRedacted(),
                    null,
                    ToolCallStatus.FAILED,
                    attempt,
                    durationMs,
                    errorCode,
                    limit(exception.getMessage(), 1000),
                    startedAt,
                    LocalDateTime.now()
            ));
            updateToolCount(task);
            appendEvent(
                    task.id(),
                    task.status(),
                    AgentEventType.TOOL_FAILED,
                    Map.of(
                            "toolCallId", callId,
                            "toolName", toolName.value(),
                            "errorCode", errorCode
                    )
            );
            throw exception;
        }
    }

    private void updateToolCount(AgentTask task) {
        AgentTask current = repository.findTask(task.projectId(), task.id()).orElse(task);
        repository.updateProgress(
                task.id(),
                current.status(),
                current.currentStep(),
                current.toolCallCount() + 1,
                current.replanCount(),
                current.resultSummary(),
                current.errorCode(),
                current.errorMessage(),
                current.startedAt(),
                current.completedAt()
        );
    }

    private void completeSuccess(Long projectId, Long taskId, AgentTestReport report) {
        AgentTask task = requireTask(projectId, taskId);
        checkRunnable(task);
        stateMachine.assertTransition(task.status(), AgentTaskStatus.SUCCEEDED);
        repository.updateProgress(
                taskId,
                AgentTaskStatus.SUCCEEDED,
                task.plan().size(),
                task.toolCallCount(),
                task.replanCount(),
                report.summary(),
                null,
                null,
                task.startedAt(),
                LocalDateTime.now()
        );
        repository.appendMessage(new com.dochelper.agent.domain.AgentMessage(
                IdWorker.getId(),
                task.conversationId(),
                taskId,
                "ASSISTANT",
                sanitizer.sanitizeText(report.summary()),
                LocalDateTime.now()
        ));
        appendEvent(
                taskId,
                AgentTaskStatus.SUCCEEDED,
                AgentEventType.REPORT_GENERATED,
                report
        );
        appendEvent(
                taskId,
                AgentTaskStatus.SUCCEEDED,
                AgentEventType.TASK_COMPLETED,
                Map.of("status", AgentTaskStatus.SUCCEEDED.name())
        );
        runtimeRegistry.remove(taskId);
    }

    private void transition(
            AgentTask task,
            AgentTaskStatus next,
            AgentEventType eventType,
            Object payload
    ) {
        stateMachine.assertTransition(task.status(), next);
        LocalDateTime startedAt = task.startedAt() == null ? LocalDateTime.now() : task.startedAt();
        repository.updateProgress(
                task.id(),
                next,
                task.currentStep(),
                task.toolCallCount(),
                task.replanCount(),
                task.resultSummary(),
                task.errorCode(),
                task.errorMessage(),
                startedAt,
                null
        );
        appendEvent(task.id(), next, eventType, payload);
    }

    private void checkRunnable(AgentTask task) {
        if (task.cancelRequested()) {
            throw new BusinessException(AgentErrorCode.TASK_CANCELLED);
        }
        if (task.status().terminal()) {
            throw new BusinessException(AgentErrorCode.INVALID_STATE);
        }
        if (LocalDateTime.now().isAfter(task.deadlineAt())) {
            throw new BusinessException(AgentErrorCode.TASK_TIMEOUT);
        }
    }

    void failDispatch(Long projectId, Long taskId, Exception exception) {
        failSafely(projectId, taskId, exception);
    }

    private void failSafely(Long projectId, Long taskId, Exception exception) {
        AgentTask task = repository.findTask(projectId, taskId).orElse(null);
        if (task == null || task.status().terminal()) {
            return;
        }
        boolean cancelled = exception instanceof BusinessException business
                && business.getErrorCode() == AgentErrorCode.TASK_CANCELLED;
        boolean needsReview = exception instanceof BusinessException business
                && ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW.code()
                .equals(business.getErrorCode().code());
        AgentTaskStatus status = cancelled
                ? AgentTaskStatus.CANCELLED
                : needsReview ? AgentTaskStatus.NEEDS_REVIEW : AgentTaskStatus.FAILED;
        String errorCode = cancelled
                ? "AGENT_CANCELLED"
                : exception instanceof BusinessException business
                ? business.getErrorCode().code()
                : "AGENT_INTERNAL_ERROR";
        String errorMessage = limit(safeMessage(exception.getMessage()), 1000);
        stateMachine.assertTransition(task.status(), status);
        repository.updateProgress(
                taskId,
                status,
                task.currentStep(),
                task.toolCallCount(),
                task.replanCount(),
                null,
                errorCode,
                errorMessage,
                task.startedAt(),
                LocalDateTime.now()
        );
        try {
            AgentTestReport terminalReport = tools.generateTerminalReport(
                    projectId,
                    taskId,
                    task.status().name(),
                    errorCode,
                    errorMessage
            );
            appendEvent(
                    taskId,
                    status,
                    AgentEventType.REPORT_GENERATED,
                    terminalReport
            );
        } catch (RuntimeException reportException) {
            LOGGER.error(
                    "Agent 终态报告生成失败：projectId={}, taskId={}, status={}, message={}",
                    projectId, taskId, status, safeMessage(reportException.getMessage()), reportException
            );
        }
        appendEvent(
                taskId,
                status,
                cancelled
                        ? AgentEventType.TASK_CANCELLED
                        : needsReview ? AgentEventType.MANUAL_REVIEW_REQUIRED : AgentEventType.TASK_COMPLETED,
                Map.of(
                        "status", status.name(),
                        "errorCode", errorCode,
                        "errorMessage", errorMessage
                )
        );
        logTaskFailure(projectId, taskId, task.status(), errorCode, errorMessage, exception, cancelled);
        runtimeRegistry.remove(taskId);
    }

    /**
     * 按异常类型记录异步任务终止原因，避免失败只落库而无法从控制台定位。
     */
    private void logTaskFailure(
            Long projectId,
            Long taskId,
            AgentTaskStatus previousStatus,
            String errorCode,
            String errorMessage,
            Exception exception,
            boolean cancelled
    ) {
        if (cancelled) {
            LOGGER.info(
                    "Agent 任务已取消：projectId={}, taskId={}, previousStatus={}, errorCode={}, errorMessage={}",
                    projectId, taskId, previousStatus, errorCode, errorMessage
            );
            return;
        }
        if (exception instanceof BusinessException) {
            LOGGER.warn(
                    "Agent 任务执行失败：projectId={}, taskId={}, previousStatus={}, errorCode={}, errorMessage={}",
                    projectId, taskId, previousStatus, errorCode, errorMessage
            );
            return;
        }
        LOGGER.error(
                "Agent 任务发生未预期异常：projectId={}, taskId={}, previousStatus={}, errorCode={}, errorMessage={}",
                projectId, taskId, previousStatus, errorCode, errorMessage, exception
        );
    }

    private AgentTask requireTask(Long projectId, Long taskId) {
        return repository.findTask(projectId, taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TASK_NOT_FOUND));
    }

    void validatePlan(List<AgentPlanStep> plan) {
        if (plan == null || plan.isEmpty() || plan.size() > properties.maxSteps()) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_PLAN,
                    "计划步骤数必须在 1 到 " + properties.maxSteps() + " 之间"
            );
        }
        for (int index = 0; index < plan.size(); index++) {
            AgentPlanStep step = plan.get(index);
            if (step == null || step.index() != index || step.request() == null) {
                throw new BusinessException(
                        AgentErrorCode.INVALID_PLAN,
                        "计划步骤序号必须连续且请求不能为空"
                );
            }
            Set<ConstraintViolation<ExecutionStepRequest>> violations =
                    validator.validate(step.request());
            if (!violations.isEmpty()) {
                String detail = violations.stream()
                        .map(violation -> violation.getPropertyPath()
                                + " " + violation.getMessage())
                        .sorted()
                        .findFirst()
                        .orElse("请求结构不合法");
                throw new BusinessException(
                        AgentErrorCode.INVALID_PLAN,
                        "第 " + index + " 个计划步骤校验失败：" + detail
                );
            }
        }
    }

    private boolean replannable(String errorCode) {
        return "EXECUTOR_400_002".equals(errorCode)
                || "EXECUTOR_422_001".equals(errorCode)
                || "EXECUTOR_422_002".equals(errorCode);
    }

    private void appendEvent(
            Long taskId,
            AgentTaskStatus state,
            AgentEventType type,
            Object payload
    ) {
        repository.appendEvent(new AgentTaskEvent(
                IdWorker.getId(),
                taskId,
                0,
                type,
                state,
                toSanitizedJson(payload),
                LocalDateTime.now()
        ));
    }

    private Object sanitizedValue(Object value) {
        JsonNode source = objectMapper.valueToTree(value);
        return objectMapper.convertValue(sanitizer.sanitizeJson(source), Object.class);
    }

    private String toSanitizedJson(Object value) {
        return toJson(sanitizedValue(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 审计 JSON 序列化失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    private String safeMessage(String value) {
        return sanitizer.sanitizeText(value == null ? "未知错误" : value);
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

}
