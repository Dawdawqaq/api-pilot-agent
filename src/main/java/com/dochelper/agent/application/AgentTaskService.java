package com.dochelper.agent.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.api.dto.ConfirmationDecisionRequest;
import com.dochelper.agent.api.dto.CreateAgentTaskRequest;
import com.dochelper.agent.api.dto.ModifyPlanRequest;
import com.dochelper.agent.api.vo.AgentConfirmationResponse;
import com.dochelper.agent.api.vo.AgentModelCallResponse;
import com.dochelper.agent.api.vo.AgentTaskEventResponse;
import com.dochelper.agent.api.vo.AgentTaskResponse;
import com.dochelper.agent.api.vo.AgentToolCallResponse;
import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.AgentConfirmation;
import com.dochelper.agent.domain.AgentConversation;
import com.dochelper.agent.domain.AgentEventType;
import com.dochelper.agent.domain.AgentMessage;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskEvent;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.ConfirmationStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.application.SensitiveDataSanitizer;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.ProjectEnvironment;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Agent 任务创建、查询、确认和取消应用服务。
 */
@Service
public class AgentTaskService {

    private final AgentTaskRepository repository;
    private final ApiProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final OpenApiCatalogRepository openApiCatalogRepository;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentTaskRunner runner;
    private final AgentPlanner planner;
    private final AgentTaskStateMachine stateMachine;
    private final TaskExecutor taskExecutor;
    private final SensitiveDataSanitizer sanitizer;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentTaskService(
            AgentTaskRepository repository,
            ApiProjectRepository projectRepository,
            ProjectEnvironmentRepository environmentRepository,
            OpenApiCatalogRepository openApiCatalogRepository,
            AgentRuntimeRegistry runtimeRegistry,
            AgentTaskRunner runner,
            AgentPlanner planner,
            AgentTaskStateMachine stateMachine,
            @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor,
            SensitiveDataSanitizer sanitizer,
            AgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.openApiCatalogRepository = openApiCatalogRepository;
        this.runtimeRegistry = runtimeRegistry;
        this.runner = runner;
        this.planner = planner;
        this.stateMachine = stateMachine;
        this.taskExecutor = taskExecutor;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建任务并异步启动 Agent。
     */
    public AgentTaskResponse create(Long projectId, CreateAgentTaskRequest request) {
        requireProject(projectId);
        ProjectEnvironment environment = requireEnvironment(projectId, request.environmentId());
        validateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        String safeGoal = limit(sanitizer.sanitizeText(request.goal().trim()), 2000);
        AgentConversation conversation = resolveConversation(
                projectId,
                request.conversationId(),
                safeGoal,
                now
        );
        Long taskId = IdWorker.getId();
        String runtimeContextRef = runtimeRegistry.create(
                taskId,
                request.goal().trim(),
                request.initialVariables(),
                request.planHint()
        );
        AgentTask task = repository.createTask(new AgentTask(
                taskId,
                projectId,
                environment.id(),
                conversation.id(),
                safeGoal,
                AgentTaskStatus.RECEIVED,
                0,
                properties.maxSteps(),
                0,
                0,
                0,
                List.of(),
                toSanitizedJson(Map.of(
                        "initialVariableNames",
                        request.initialVariables() == null
                                ? List.of()
                                : request.initialVariables().keySet(),
                        "runtimeContextRef", runtimeContextRef
                )),
                null,
                null,
                null,
                false,
                0,
                now.plus(properties.taskTimeout()),
                now,
                null,
                null,
                now
        ));
        repository.appendMessage(new AgentMessage(
                IdWorker.getId(),
                conversation.id(),
                taskId,
                "USER",
                safeGoal,
                now
        ));
        appendEvent(
                taskId,
                AgentTaskStatus.RECEIVED,
                AgentEventType.TASK_CREATED,
                Map.of(
                        "goal", safeGoal,
                        "environmentId", environment.id(),
                        "conversationId", conversation.id()
                )
        );
        taskExecutor.execute(() -> runner.runNew(projectId, taskId));
        return detail(task);
    }

    public AgentTaskResponse get(Long projectId, Long taskId) {
        requireProject(projectId);
        return detail(requireTask(projectId, taskId));
    }

    public List<AgentTaskResponse> list(Long projectId, int limit) {
        requireProject(projectId);
        return repository.findTasks(projectId, limit).stream()
                .map(this::detail)
                .toList();
    }

    public List<AgentTaskEventResponse> events(
            Long projectId,
            Long taskId,
            long afterSequence,
            int limit
    ) {
        requireTask(projectId, taskId);
        return repository.findEvents(taskId, afterSequence, limit).stream()
                .map(event -> AgentTaskEventResponse.from(event, objectMapper))
                .toList();
    }

    /**
     * 对待确认的危险计划做一次性原子决策。
     */
    public AgentTaskResponse confirm(
            Long projectId,
            Long taskId,
            Long decidedByUserId,
            ConfirmationDecisionRequest request
    ) {
        AgentTask task = requireTask(projectId, taskId);
        if (task.status() != AgentTaskStatus.WAITING_CONFIRMATION) {
            throw new BusinessException(AgentErrorCode.INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now();
        String confirmOwner = "confirm-" + UUID.randomUUID();
        boolean acquired = repository.tryAcquireLease(taskId, confirmOwner, now, now.plus(properties.leaseDuration()));
        if (!acquired) {
            throw new BusinessException(AgentErrorCode.TASK_BUSY, "当前任务正在修改或规划处理中，请稍后确认");
        }
        try {
            AgentConfirmation confirmation = repository.findConfirmation(taskId, task.currentStep())
                    .orElseThrow(() -> new BusinessException(
                            AgentErrorCode.CONFIRMATION_NOT_FOUND
                    ));
            if (now.isAfter(confirmation.expiresAt())) {
                repository.decideConfirmation(
                        confirmation.id(),
                        ConfirmationStatus.PENDING,
                        ConfirmationStatus.EXPIRED,
                        "确认超时",
                        decidedByUserId,
                        now
                );
                failTask(task, AgentErrorCode.CONFIRMATION_EXPIRED);
                throw new BusinessException(AgentErrorCode.CONFIRMATION_EXPIRED);
            }
            ConfirmationStatus decision = request.approved()
                    ? ConfirmationStatus.APPROVED
                    : ConfirmationStatus.REJECTED;
            boolean updated = repository.decideConfirmation(
                    confirmation.id(),
                    ConfirmationStatus.PENDING,
                    decision,
                    sanitizer.sanitizeText(request.note()),
                    decidedByUserId,
                    now
            );
            if (!updated) {
                throw new BusinessException(AgentErrorCode.INVALID_STATE, "该确认已被处理");
            }
            appendEvent(
                    taskId,
                    task.status(),
                    AgentEventType.CONFIRMATION_DECIDED,
                    Map.of("approved", request.approved(), "note", safeMapValue(request.note()))
            );
            if (request.approved()) {
                taskExecutor.execute(() -> runner.resumeApproved(projectId, taskId));
            } else {
                repository.requestCancel(taskId);
                stateMachine.assertTransition(task.status(), AgentTaskStatus.CANCELLED);
                repository.updateProgress(
                        taskId,
                        AgentTaskStatus.CANCELLED,
                        task.currentStep(),
                        task.toolCallCount(),
                        task.replanCount(),
                        "用户拒绝危险操作",
                        "AGENT_CONFIRMATION_REJECTED",
                        "用户拒绝危险操作",
                        task.startedAt(),
                        now
                );
                appendEvent(
                        taskId,
                        AgentTaskStatus.CANCELLED,
                        AgentEventType.TASK_CANCELLED,
                        Map.of("reason", "用户拒绝危险操作")
                );
                runtimeRegistry.remove(taskId);
            }
            return get(projectId, taskId);
        } finally {
            repository.releaseLease(taskId, confirmOwner);
        }
    }

    /**
     * 人工多轮对话修改未执行的计划。
     *
     * @param projectId 项目 ID
     * @param taskId 任务 ID
     * @param request 修改请求
     * @return 修改后的任务详情响应
     */
    public AgentTaskResponse modifyPlan(Long projectId, Long taskId, ModifyPlanRequest request) {
        requireProject(projectId);
        AgentTask task = requireTask(projectId, taskId);
        if (task.status() != AgentTaskStatus.WAITING_CONFIRMATION) {
            throw new BusinessException(AgentErrorCode.INVALID_STATE, "只有待人工确认的任务才支持修改计划");
        }
        if (task.modificationCount() >= properties.maxPlanModifications()) {
            throw new BusinessException(
                    AgentErrorCode.PLAN_MODIFICATION_LIMIT,
                    "任务计划修改轮次已达上限（" + properties.maxPlanModifications() + " 次），请直接确认执行或取消任务重新发起"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        String modifyOwner = "modify-" + UUID.randomUUID();
        boolean acquired = repository.tryAcquireLease(
                taskId,
                modifyOwner,
                now,
                now.plus(properties.leaseDuration())
        );
        if (!acquired) {
            throw new BusinessException(
                    AgentErrorCode.TASK_BUSY,
                    "当前任务正在修改或规划处理中，请勿重复操作"
            );
        }

        try {
            AgentTask currentTask = requireTask(projectId, taskId);
            if (currentTask.status() != AgentTaskStatus.WAITING_CONFIRMATION) {
                throw new BusinessException(AgentErrorCode.INVALID_STATE);
            }
            if (currentTask.modificationCount() >= properties.maxPlanModifications()) {
                throw new BusinessException(AgentErrorCode.PLAN_MODIFICATION_LIMIT);
            }

            List<ApiEndpoint> endpoints = openApiCatalogRepository.findEndpoints(projectId, null);
            String rawInstruction = limit(request.instruction().trim(), 2000);
            Set<String> variableNames = runtimeRegistry.find(taskId)
                    .map(rt -> rt.initialVariables() == null ? Set.<String>of() : rt.initialVariables().keySet())
                    .orElse(Set.of());

            AgentModifyPlanContext context = new AgentModifyPlanContext(
                    taskId,
                    projectId,
                    currentTask.goal(),
                    currentTask.plan(),
                    rawInstruction,
                    endpoints,
                    variableNames
            );

            List<AgentPlanStep> revisedPlan = planner.modify(context);
            if (revisedPlan == null || revisedPlan.isEmpty()) {
                throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "修改后的计划不能为空");
            }

            int nextCount = currentTask.modificationCount() + 1;
            String revisedPlanJson = toJson(revisedPlan);
            boolean updated = repository.updateModifiedPlan(
                    taskId,
                    revisedPlanJson,
                    currentTask.modificationCount(),
                    nextCount
            );
            if (!updated) {
                throw new BusinessException(AgentErrorCode.TASK_BUSY, "计划修改发生并发冲突，请重试");
            }

            String newPlanHash = sha256(revisedPlanJson);
            repository.refreshPendingConfirmation(
                    taskId,
                    currentTask.currentStep(),
                    newPlanHash,
                    now.plus(properties.confirmationTimeout())
            );

            appendEvent(
                    taskId,
                    currentTask.status(),
                    AgentEventType.PLAN_REVISED,
                    Map.of(
                            "modificationCount", nextCount,
                            "remainingModifications", Math.max(0, properties.maxPlanModifications() - nextCount),
                            "instruction", safeMapValue(request.instruction()),
                            "planHash", newPlanHash
                    )
            );

            return get(projectId, taskId);
        } finally {
            repository.releaseLease(taskId, modifyOwner);
        }
    }

    /**
     * 请求取消任务，运行线程会在工具边界协作式终止。
     */
    public AgentTaskResponse cancel(Long projectId, Long taskId) {
        AgentTask task = requireTask(projectId, taskId);
        if (task.status().terminal()) {
            throw new BusinessException(AgentErrorCode.INVALID_STATE, "终态任务不能取消");
        }
        repository.requestCancel(taskId);
        appendEvent(
                taskId,
                task.status(),
                AgentEventType.CANCEL_REQUESTED,
                Map.of("requestedAt", LocalDateTime.now().toString())
        );
        if (task.status() == AgentTaskStatus.WAITING_CONFIRMATION
                || task.status() == AgentTaskStatus.RECEIVED) {
            stateMachine.assertTransition(task.status(), AgentTaskStatus.CANCELLED);
            repository.updateProgress(
                    taskId,
                    AgentTaskStatus.CANCELLED,
                    task.currentStep(),
                    task.toolCallCount(),
                    task.replanCount(),
                    null,
                    "AGENT_CANCELLED",
                    "用户取消任务",
                    task.startedAt(),
                    LocalDateTime.now()
            );
            appendEvent(
                    taskId,
                    AgentTaskStatus.CANCELLED,
                    AgentEventType.TASK_CANCELLED,
                    Map.of("reason", "用户取消任务")
            );
            runtimeRegistry.remove(taskId);
        }
        return get(projectId, taskId);
    }

    private AgentTaskResponse detail(AgentTask task) {
        AgentConfirmationResponse confirmation = repository
                .findConfirmation(task.id(), task.currentStep())
                .map(AgentConfirmationResponse::from)
                .orElse(null);
        List<AgentToolCallResponse> calls = repository.findToolCalls(task.id()).stream()
                .map(call -> AgentToolCallResponse.from(call, objectMapper))
                .toList();
        List<AgentModelCallResponse> modelCalls = repository.findModelCalls(task.id()).stream()
                .map(AgentModelCallResponse::from)
                .toList();
        return AgentTaskResponse.from(task, confirmation, calls, modelCalls);
    }

    private AgentConversation resolveConversation(
            Long projectId,
            Long conversationId,
            String goal,
            LocalDateTime now
    ) {
        if (conversationId != null) {
            return repository.findConversation(projectId, conversationId)
                    .orElseThrow(() -> new BusinessException(
                            AgentErrorCode.INVALID_TASK,
                            "会话不存在或不属于当前项目"
                    ));
        }
        return repository.createConversation(new AgentConversation(
                IdWorker.getId(),
                projectId,
                limit(goal, 200),
                "ACTIVE",
                now,
                now
        ));
    }

    private void validateRequest(CreateAgentTaskRequest request) {
        int planSize = request.planHint() == null ? 0 : request.planHint().size();
        if (planSize > properties.maxSteps()) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_PLAN,
                    "计划提示不能超过 " + properties.maxSteps() + " 步"
            );
        }
        if (request.initialVariables() != null && request.initialVariables().size() > 100) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_TASK,
                    "初始变量不能超过 100 个"
            );
        }
    }

    private ProjectEnvironment requireEnvironment(Long projectId, Long environmentId) {
        if (environmentId != null) {
            return environmentRepository.findByIdAndProjectId(environmentId, projectId)
                    .orElseThrow(() -> new BusinessException(
                            ProjectErrorCode.ENVIRONMENT_NOT_FOUND
                    ));
        }
        return environmentRepository.findByProjectId(projectId).stream()
                .filter(ProjectEnvironment::defaultEnvironment)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        AgentErrorCode.INVALID_TASK,
                        "项目没有默认执行环境"
                ));
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private AgentTask requireTask(Long projectId, Long taskId) {
        requireProject(projectId);
        return repository.findTask(projectId, taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TASK_NOT_FOUND));
    }

    private void failTask(AgentTask task, AgentErrorCode errorCode) {
        stateMachine.assertTransition(task.status(), AgentTaskStatus.FAILED);
        repository.updateProgress(
                task.id(),
                AgentTaskStatus.FAILED,
                task.currentStep(),
                task.toolCallCount(),
                task.replanCount(),
                null,
                errorCode.code(),
                errorCode.message(),
                task.startedAt(),
                LocalDateTime.now()
        );
        runtimeRegistry.remove(task.id());
    }

    private void appendEvent(
            Long taskId,
            AgentTaskStatus state,
            AgentEventType eventType,
            Object payload
    ) {
        repository.appendEvent(new AgentTaskEvent(
                IdWorker.getId(),
                taskId,
                0,
                eventType,
                state,
                toSanitizedJson(payload),
                LocalDateTime.now()
        ));
    }

    private String toSanitizedJson(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    sanitizer.sanitizeJson(objectMapper.valueToTree(value))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 事件 JSON 序列化失败", exception);
        }
    }

    private String safeMapValue(String value) {
        String sanitized = sanitizer.sanitizeText(value);
        return sanitized == null ? "" : sanitized;
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 计划 JSON 序列化失败", exception);
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("缺少 SHA-256 算法", exception);
        }
    }
}
