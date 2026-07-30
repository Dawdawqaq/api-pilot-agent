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
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.retrieval.domain.RetrievalResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * 执行 Agent 的检索、规划、行动、观察、重规划和报告循环。
 */
@Component
public class AgentTaskRunner {

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
    private final Set<Long> runningTasks = ConcurrentHashMap.newKeySet();

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
            Validator validator
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
    }

    /**
     * 启动新任务的 ReAct 流程。
     */
    public void runNew(Long projectId, Long taskId) {
        if (!runningTasks.add(taskId)) {
            return;
        }
        try {
            AgentTask task = requireTask(projectId, taskId);
            checkRunnable(task);
            transition(task, AgentTaskStatus.RETRIEVING, AgentEventType.STATE_CHANGED, Map.of());
            task = requireTask(projectId, taskId);

            AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.find(taskId)
                    .orElseThrow(() -> new BusinessException(
                            AgentErrorCode.INVALID_STATE,
                            "任务运行上下文已丢失，请重新创建任务"
                    ));
            List<RetrievalResult> evidence = invokeTool(
                    task,
                    0,
                    AgentToolName.SEARCH_API_DOCUMENT,
                    Map.of("projectId", projectId, "query", runtime.goal(), "topK", 5),
                    () -> tools.searchApiDocument(projectId, runtime.goal(), 5)
            );
            appendEvent(
                    taskId,
                    AgentTaskStatus.RETRIEVING,
                    AgentEventType.RETRIEVAL_COMPLETED,
                    Map.of(
                            "resultCount", evidence.size(),
                            "citations", evidence.stream().map(RetrievalResult::citation).toList()
                    )
            );

            task = requireTask(projectId, taskId);
            transition(task, AgentTaskStatus.PLANNING, AgentEventType.STATE_CHANGED, Map.of());
            List<ApiEndpoint> endpoints = openApiRepository.findEndpoints(projectId, null);
            List<AgentPlanStep> plan = planner.plan(new AgentPlanningContext(
                    taskId,
                    projectId,
                    runtime.goal(),
                    evidence,
                    endpoints,
                    Set.copyOf(runtime.initialVariables().keySet()),
                    runtime.planHint()
            ));
            validatePlan(plan);
            runtimeRegistry.updatePlan(taskId, plan);
            repository.updatePlan(
                    taskId,
                    toSanitizedJson(plan),
                    toSanitizedJson(Map.of(
                            "initialVariables", sanitizer.sanitizeVariables(runtime.initialVariables()),
                            "citations", evidence.stream().map(RetrievalResult::citation).toList()
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
            runningTasks.remove(taskId);
        }
    }

    /**
     * 人工批准后继续执行已规划任务。
     */
    public void resumeApproved(Long projectId, Long taskId) {
        if (!runningTasks.add(taskId)) {
            return;
        }
        try {
            AgentTask task = requireTask(projectId, taskId);
            if (task.status() != AgentTaskStatus.WAITING_CONFIRMATION) {
                throw new BusinessException(AgentErrorCode.INVALID_STATE);
            }
            AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.find(taskId)
                    .orElseThrow(() -> new BusinessException(
                            AgentErrorCode.INVALID_STATE,
                            "未脱敏运行上下文已随应用重启清除，请重新创建任务"
                    ));
            executePlan(projectId, taskId, runtime.plan());
        } catch (Exception exception) {
            failSafely(projectId, taskId, exception);
        } finally {
            runningTasks.remove(taskId);
        }
    }

    private void executePlan(Long projectId, Long taskId, List<AgentPlanStep> plan) {
        AgentTask task = requireTask(projectId, taskId);
        checkRunnable(task);
        transition(task, AgentTaskStatus.EXECUTING, AgentEventType.STATE_CHANGED, Map.of());
        AgentRuntimeRegistry.RuntimeContext runtime = runtimeRegistry.find(taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.INVALID_STATE));
        List<ExecutionStepRequest> approvedSteps = plan.stream()
                .map(step -> approveDangerous(step.request()))
                .toList();
        ExecuteScenarioRequest request = new ExecuteScenarioRequest(
                task.environmentId(),
                runtime.initialVariables(),
                approvedSteps
        );

        int replanCount = task.replanCount();
        ScenarioExecutionResult result;
        while (true) {
            checkRunnable(requireTask(projectId, taskId));
            task = requireTask(projectId, taskId);
            result = invokeTool(
                    task,
                    task.currentStep(),
                    AgentToolName.EXECUTE_HTTP_REQUEST,
                    request,
                    () -> tools.executeHttpRequest(projectId, request)
            );
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
            if (result.status() == com.dochelper.executor.domain.ExecutionStatus.SUCCEEDED) {
                break;
            }
            if (!recoverable(result.errorCode()) || replanCount >= properties.maxReplans()) {
                throw new BusinessException(
                        AgentErrorCode.INVALID_PLAN,
                        "执行计划失败：" + safeMessage(result.errorMessage())
                );
            }
            replanCount++;
            AgentTask current = requireTask(projectId, taskId);
            stateMachine.assertTransition(current.status(), AgentTaskStatus.REPLANNING);
            repository.updateProgress(
                    taskId,
                    AgentTaskStatus.REPLANNING,
                    current.currentStep(),
                    current.toolCallCount(),
                    replanCount,
                    null,
                    null,
                    null,
                    current.startedAt(),
                    null
            );
            appendEvent(
                    taskId,
                    AgentTaskStatus.REPLANNING,
                    AgentEventType.REPLAN_STARTED,
                    Map.of("replanCount", replanCount, "reason", safeMessage(result.errorMessage()))
            );
            transition(
                    requireTask(projectId, taskId),
                    AgentTaskStatus.EXECUTING,
                    AgentEventType.STATE_CHANGED,
                    Map.of("strategy", "对可恢复网络错误重试原计划")
            );
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

    private void waitForConfirmation(
            Long projectId,
            Long taskId,
            AgentPlanStep dangerous,
            List<AgentPlanStep> plan
    ) {
        AgentTask task = requireTask(projectId, taskId);
        LocalDateTime now = LocalDateTime.now();
        repository.createConfirmation(new AgentConfirmation(
                IdWorker.getId(),
                taskId,
                dangerous.index(),
                ConfirmationStatus.PENDING,
                toSanitizedJson(plan.stream().filter(AgentPlanStep::dangerous).toList()),
                null,
                now.plus(properties.confirmationTimeout()),
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
                        "expiresAt", now.plus(properties.confirmationTimeout()).toString()
                )
        );
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
            throw new TaskCancelledException();
        }
        if (LocalDateTime.now().isAfter(task.deadlineAt())) {
            throw new BusinessException(AgentErrorCode.TASK_TIMEOUT);
        }
    }

    private void failSafely(Long projectId, Long taskId, Exception exception) {
        AgentTask task = repository.findTask(projectId, taskId).orElse(null);
        if (task == null || task.status().terminal()) {
            return;
        }
        boolean cancelled = exception instanceof TaskCancelledException;
        AgentTaskStatus status = cancelled ? AgentTaskStatus.CANCELLED : AgentTaskStatus.FAILED;
        String errorCode = cancelled
                ? "AGENT_CANCELLED"
                : exception instanceof BusinessException business
                ? business.getErrorCode().code()
                : "AGENT_INTERNAL_ERROR";
        stateMachine.assertTransition(task.status(), status);
        repository.updateProgress(
                taskId,
                status,
                task.currentStep(),
                task.toolCallCount(),
                task.replanCount(),
                null,
                errorCode,
                limit(safeMessage(exception.getMessage()), 1000),
                task.startedAt(),
                LocalDateTime.now()
        );
        appendEvent(
                taskId,
                status,
                cancelled ? AgentEventType.TASK_CANCELLED : AgentEventType.TASK_COMPLETED,
                Map.of("status", status.name(), "errorCode", errorCode)
        );
        runtimeRegistry.remove(taskId);
    }

    private AgentTask requireTask(Long projectId, Long taskId) {
        return repository.findTask(projectId, taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TASK_NOT_FOUND));
    }

    private void validatePlan(List<AgentPlanStep> plan) {
        if (plan == null || plan.isEmpty() || plan.size() > properties.maxSteps()) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_PLAN,
                    "计划步骤数必须在 1 到 " + properties.maxSteps() + " 之间"
            );
        }
        for (int index = 0; index < plan.size(); index++) {
            AgentPlanStep step = plan.get(index);
            if (step.index() != index || step.request() == null) {
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

    private ExecutionStepRequest approveDangerous(ExecutionStepRequest request) {
        Boolean dangerousOperationConfirmed = "DELETE".equalsIgnoreCase(request.method())
                ? Boolean.TRUE
                : request.dangerousOperationConfirmed();
        return new ExecutionStepRequest(
                request.name(),
                request.method(),
                request.path(),
                request.pathVariables(),
                request.queryParams(),
                request.headers(),
                request.body(),
                request.authentication(),
                request.extractors(),
                request.assertions(),
                dangerousOperationConfirmed
        );
    }

    private boolean recoverable(String errorCode) {
        return "EXECUTOR_502_001".equals(errorCode)
                || "EXECUTOR_504_001".equals(errorCode);
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

    /**
     * 用于区分用户取消和业务失败。
     */
    private static final class TaskCancelledException extends RuntimeException {
    }
}
