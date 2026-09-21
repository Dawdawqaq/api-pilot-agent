package com.dochelper.agent.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.agent.domain.AgentConfirmation;
import com.dochelper.agent.domain.AgentConversation;
import com.dochelper.agent.domain.AgentEventType;
import com.dochelper.agent.domain.AgentMessage;
import com.dochelper.agent.domain.AgentModelCall;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskEvent;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.AgentToolCall;
import com.dochelper.agent.domain.ConfirmationStatus;
import com.dochelper.agent.domain.ToolCallStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.infrastructure.persistence.entity.AgentConfirmationEntity;
import com.dochelper.agent.infrastructure.persistence.entity.AgentConversationEntity;
import com.dochelper.agent.infrastructure.persistence.entity.AgentMessageEntity;
import com.dochelper.agent.infrastructure.persistence.entity.AgentModelCallEntity;
import com.dochelper.agent.infrastructure.persistence.entity.AgentTaskEntity;
import com.dochelper.agent.infrastructure.persistence.entity.AgentTaskEventEntity;
import com.dochelper.agent.infrastructure.persistence.entity.AgentToolCallEntity;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentConfirmationMapper;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentConversationMapper;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentMessageMapper;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentModelCallMapper;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentTaskEventMapper;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentTaskMapper;
import com.dochelper.agent.infrastructure.persistence.mapper.AgentToolCallMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis-Plus 的 Agent 聚合仓储实现。
 */
@Repository
public class MybatisAgentTaskRepository implements AgentTaskRepository {

    private static final String ACTIVE_PROJECT_EXISTS_SQL =
            "EXISTS (SELECT 1 FROM api_project p WHERE p.id = agent_task.project_id AND p.deleted = 0)";

    private final AgentConversationMapper conversationMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskEventMapper eventMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentModelCallMapper modelCallMapper;
    private final AgentConfirmationMapper confirmationMapper;
    private final AgentMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public MybatisAgentTaskRepository(
            AgentConversationMapper conversationMapper,
            AgentTaskMapper taskMapper,
            AgentTaskEventMapper eventMapper,
            AgentToolCallMapper toolCallMapper,
            AgentModelCallMapper modelCallMapper,
            AgentConfirmationMapper confirmationMapper,
            AgentMessageMapper messageMapper,
            ObjectMapper objectMapper
    ) {
        this.conversationMapper = conversationMapper;
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.toolCallMapper = toolCallMapper;
        this.modelCallMapper = modelCallMapper;
        this.confirmationMapper = confirmationMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentConversation createConversation(AgentConversation value) {
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setTitle(value.title());
        entity.setStatus(value.status());
        entity.setCreatedAt(value.createdAt());
        entity.setUpdatedAt(value.updatedAt());
        entity.setDeleted(false);
        conversationMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<AgentConversation> findConversation(Long projectId, Long conversationId) {
        return Optional.ofNullable(conversationMapper.selectOne(
                Wrappers.<AgentConversationEntity>lambdaQuery()
                        .eq(AgentConversationEntity::getId, conversationId)
                        .eq(AgentConversationEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public AgentTask createTask(AgentTask value) {
        AgentTaskEntity entity = toEntity(value);
        taskMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public void appendMessage(AgentMessage value) {
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setId(value.id());
        entity.setConversationId(value.conversationId());
        entity.setTaskId(value.taskId());
        entity.setRole(value.role());
        entity.setContentRedacted(value.contentRedacted());
        entity.setCreatedAt(value.createdAt());
        messageMapper.insert(entity);
    }

    @Override
    public Optional<AgentTask> findTask(Long projectId, Long taskId) {
        return Optional.ofNullable(taskMapper.selectOne(
                Wrappers.<AgentTaskEntity>lambdaQuery()
                        .eq(AgentTaskEntity::getId, taskId)
                        .eq(AgentTaskEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<AgentTask> findTasks(Long projectId, int limit) {
        return taskMapper.selectList(
                Wrappers.<AgentTaskEntity>lambdaQuery()
                        .eq(AgentTaskEntity::getProjectId, projectId)
                        .orderByDesc(AgentTaskEntity::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100)))
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public long countActiveTasks() {
        return taskMapper.selectCount(Wrappers.<AgentTaskEntity>lambdaQuery()
                .apply(ACTIVE_PROJECT_EXISTS_SQL)
                .notIn(AgentTaskEntity::getStatus, terminalStatuses()));
    }

    @Override
    public long countActiveTasks(Long projectId) {
        return taskMapper.selectCount(Wrappers.<AgentTaskEntity>lambdaQuery()
                .eq(AgentTaskEntity::getProjectId, projectId)
                .apply(ACTIVE_PROJECT_EXISTS_SQL)
                .notIn(AgentTaskEntity::getStatus, terminalStatuses()));
    }

    @Override
    public List<AgentTask> findRecoverableTasks(LocalDateTime now, int limit) {
        return taskMapper.selectList(Wrappers.<AgentTaskEntity>lambdaQuery()
                        .apply(ACTIVE_PROJECT_EXISTS_SQL)
                        .notIn(AgentTaskEntity::getStatus, terminalStatuses())
                        // 未到期的待确认任务由确认入口按需恢复，避免占满恢复批次。
                        .and(wrapper -> wrapper.ne(AgentTaskEntity::getStatus, AgentTaskStatus.WAITING_CONFIRMATION.name())
                                .or().le(AgentTaskEntity::getDeadlineAt, now)
                                .or().apply("EXISTS (SELECT 1 FROM agent_confirmation c WHERE c.task_id = agent_task.id "
                                        + "AND c.id = (SELECT MAX(c2.id) FROM agent_confirmation c2 WHERE c2.task_id = agent_task.id) "
                                        + "AND (c.status = 'APPROVED' OR (c.status = 'PENDING' AND c.expires_at <= {0})))", now))
                        .and(wrapper -> wrapper.isNull(AgentTaskEntity::getLeaseUntil)
                                .or().lt(AgentTaskEntity::getLeaseUntil, now))
                        .orderByAsc(AgentTaskEntity::getUpdatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean tryAcquireLease(
            Long taskId,
            String owner,
            LocalDateTime now,
            LocalDateTime leaseUntil
    ) {
        return taskMapper.update(null, Wrappers.<AgentTaskEntity>lambdaUpdate()
                .eq(AgentTaskEntity::getId, taskId)
                .apply(ACTIVE_PROJECT_EXISTS_SQL)
                .notIn(AgentTaskEntity::getStatus, terminalStatuses())
                .and(wrapper -> wrapper.isNull(AgentTaskEntity::getLeaseUntil)
                        .or().lt(AgentTaskEntity::getLeaseUntil, now)
                        .or().eq(AgentTaskEntity::getLeaseOwner, owner))
                .set(AgentTaskEntity::getLeaseOwner, owner)
                .set(AgentTaskEntity::getLeaseUntil, leaseUntil)
                .set(AgentTaskEntity::getClaimedAt, now)) == 1;
    }

    @Override
    public boolean renewLease(Long taskId, String owner, LocalDateTime leaseUntil) {
        return taskMapper.update(null, Wrappers.<AgentTaskEntity>lambdaUpdate()
                .eq(AgentTaskEntity::getId, taskId)
                .eq(AgentTaskEntity::getLeaseOwner, owner)
                .set(AgentTaskEntity::getLeaseUntil, leaseUntil)) == 1;
    }

    @Override
    public void releaseLease(Long taskId, String owner) {
        taskMapper.update(null, Wrappers.<AgentTaskEntity>lambdaUpdate()
                .eq(AgentTaskEntity::getId, taskId)
                .eq(AgentTaskEntity::getLeaseOwner, owner)
                .set(AgentTaskEntity::getLeaseOwner, null)
                .set(AgentTaskEntity::getLeaseUntil, null));
    }

    @Override
    public void updatePlan(Long taskId, String planJson, String contextJsonRedacted) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(taskId);
        entity.setPlanJson(planJson);
        entity.setContextJsonRedacted(contextJsonRedacted);
        taskMapper.updateById(entity);
    }

    @Override
    public void updateProgress(
            Long taskId,
            AgentTaskStatus status,
            int currentStep,
            int toolCallCount,
            int replanCount,
            String resultSummary,
            String errorCode,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(taskId);
        entity.setStatus(status.name());
        entity.setCurrentStep(currentStep);
        entity.setToolCallCount(toolCallCount);
        entity.setReplanCount(replanCount);
        entity.setResultSummary(resultSummary);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setStartedAt(startedAt);
        entity.setCompletedAt(completedAt);
        // 终态不可被迟到的工作线程覆盖；取消与报告完成竞争时保留先落库的终态。
        taskMapper.update(entity, Wrappers.<AgentTaskEntity>lambdaUpdate()
                .eq(AgentTaskEntity::getId, taskId)
                .notIn(AgentTaskEntity::getStatus, terminalStatuses()));
    }

    @Override
    public void requestCancel(Long taskId) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(taskId);
        entity.setCancelRequested(true);
        taskMapper.updateById(entity);
    }

    @Override
    @Transactional
    public synchronized AgentTaskEvent appendEvent(AgentTaskEvent value) {
        AgentTaskEventEntity entity = new AgentTaskEventEntity();
        entity.setId(value.id());
        entity.setTaskId(value.taskId());
        entity.setSequenceNo(eventMapper.selectNextSequence(value.taskId()));
        entity.setEventType(value.eventType().name());
        entity.setState(value.state().name());
        entity.setPayloadJson(value.payloadJson());
        entity.setCreatedAt(value.createdAt());
        eventMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public List<AgentTaskEvent> findEvents(Long taskId, long afterSequence, int limit) {
        return eventMapper.selectList(
                Wrappers.<AgentTaskEventEntity>lambdaQuery()
                        .eq(AgentTaskEventEntity::getTaskId, taskId)
                        .gt(AgentTaskEventEntity::getSequenceNo, Math.max(0, afterSequence))
                        .orderByAsc(AgentTaskEventEntity::getSequenceNo)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500)))
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public void createToolCall(AgentToolCall value) {
        toolCallMapper.insert(toEntity(value));
    }

    @Override
    public void completeToolCall(AgentToolCall value) {
        AgentToolCallEntity entity = new AgentToolCallEntity();
        entity.setId(value.id());
        entity.setResponseJsonRedacted(value.responseJsonRedacted());
        entity.setStatus(value.status().name());
        entity.setDurationMs(value.durationMs());
        entity.setErrorCode(value.errorCode());
        entity.setErrorMessage(value.errorMessage());
        entity.setCompletedAt(value.completedAt());
        toolCallMapper.updateById(entity);
    }

    @Override
    public List<AgentToolCall> findToolCalls(Long taskId) {
        return toolCallMapper.selectList(
                Wrappers.<AgentToolCallEntity>lambdaQuery()
                        .eq(AgentToolCallEntity::getTaskId, taskId)
                        // 重规划会重新使用步骤编号，执行轨迹必须按实际发生时间展示。
                        .orderByAsc(AgentToolCallEntity::getCreatedAt)
                        .orderByAsc(AgentToolCallEntity::getId)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public void interruptRunningToolCalls(Long taskId, LocalDateTime now) {
        toolCallMapper.update(null, Wrappers.<AgentToolCallEntity>lambdaUpdate()
                .eq(AgentToolCallEntity::getTaskId, taskId)
                .eq(AgentToolCallEntity::getStatus, "RUNNING")
                .set(AgentToolCallEntity::getStatus, "FAILED")
                .set(AgentToolCallEntity::getErrorCode, "AGENT_INTERRUPTED")
                .set(AgentToolCallEntity::getErrorMessage, "执行进程中断，后续恢复以步骤审计为准")
                .set(AgentToolCallEntity::getCompletedAt, now));
    }

    @Override
    public void createModelCall(AgentModelCall value) {
        AgentModelCallEntity entity = new AgentModelCallEntity();
        entity.setId(value.id());
        entity.setTaskId(value.taskId());
        entity.setModelName(value.modelName());
        entity.setStatus(value.status());
        entity.setAttempt(value.attempt());
        entity.setPromptTokens(value.promptTokens());
        entity.setCompletionTokens(value.completionTokens());
        entity.setTotalTokens(value.totalTokens());
        entity.setDurationMs(value.durationMs());
        entity.setErrorCode(value.errorCode());
        entity.setErrorMessage(value.errorMessage());
        entity.setCreatedAt(value.createdAt());
        entity.setCompletedAt(value.completedAt());
        modelCallMapper.insert(entity);
    }

    @Override
    public List<AgentModelCall> findModelCalls(Long taskId) {
        return modelCallMapper.selectList(
                Wrappers.<AgentModelCallEntity>lambdaQuery()
                        .eq(AgentModelCallEntity::getTaskId, taskId)
                        .orderByAsc(AgentModelCallEntity::getCreatedAt)
                        .orderByAsc(AgentModelCallEntity::getId)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public AgentConfirmation createConfirmation(AgentConfirmation value) {
        AgentConfirmationEntity entity = toEntity(value);
        confirmationMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<AgentConfirmation> findConfirmation(Long taskId, int stepIndex) {
        return Optional.ofNullable(confirmationMapper.selectOne(
                Wrappers.<AgentConfirmationEntity>lambdaQuery()
                        .eq(AgentConfirmationEntity::getTaskId, taskId)
                        .eq(AgentConfirmationEntity::getStepIndex, stepIndex)
                        .orderByDesc(AgentConfirmationEntity::getCreatedAt)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public boolean decideConfirmation(
            Long confirmationId,
            ConfirmationStatus expectedStatus,
            ConfirmationStatus decidedStatus,
            String decisionNote,
            Long decidedByUserId,
            LocalDateTime decidedAt
    ) {
        return confirmationMapper.update(
                null,
                Wrappers.<AgentConfirmationEntity>lambdaUpdate()
                        .eq(AgentConfirmationEntity::getId, confirmationId)
                        .eq(AgentConfirmationEntity::getStatus, expectedStatus.name())
                        .set(AgentConfirmationEntity::getStatus, decidedStatus.name())
                        .set(AgentConfirmationEntity::getDecisionNote, decisionNote)
                        .set(AgentConfirmationEntity::getDecidedByUserId, decidedByUserId)
                        .set(AgentConfirmationEntity::getDecidedAt, decidedAt)
        ) == 1;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void revisePendingPlan(com.dochelper.agent.domain.AgentPlanRevision revision) {
        int updated = taskMapper.update(
                null,
                Wrappers.<AgentTaskEntity>lambdaUpdate()
                        .eq(AgentTaskEntity::getId, revision.taskId())
                        .eq(AgentTaskEntity::getStatus, AgentTaskStatus.WAITING_CONFIRMATION.name())
                        .eq(AgentTaskEntity::getModificationCount, revision.expectedModificationCount())
                        .set(AgentTaskEntity::getPlanJson, revision.planJsonRedacted())
                        .set(AgentTaskEntity::getContextJsonRedacted, revision.contextJsonRedacted())
                        .set(AgentTaskEntity::getCurrentStep, revision.confirmationStepIndex())
                        .set(AgentTaskEntity::getModificationCount, revision.expectedModificationCount() + 1)
        );
        if (updated != 1) {
            throw new com.dochelper.common.exception.BusinessException(
                    com.dochelper.agent.exception.AgentErrorCode.TASK_BUSY, "计划修订状态已变化，请刷新任务"
            );
        }
        int refreshed = confirmationMapper.update(
                null,
                Wrappers.<AgentConfirmationEntity>lambdaUpdate()
                        .eq(AgentConfirmationEntity::getTaskId, revision.taskId())
                        .eq(AgentConfirmationEntity::getStatus, ConfirmationStatus.PENDING.name())
                        .set(AgentConfirmationEntity::getStepIndex, revision.confirmationStepIndex())
                        .set(AgentConfirmationEntity::getRequestJson, revision.confirmationJsonRedacted())
                        .set(AgentConfirmationEntity::getPlanHash, revision.planHash())
                        .set(AgentConfirmationEntity::getExpiresAt, revision.expiresAt())
        );
        if (refreshed != 1) {
            // 抛出异常回滚同一事务中的计划修改，避免计划与授权版本分离。
            throw new com.dochelper.common.exception.BusinessException(
                    com.dochelper.agent.exception.AgentErrorCode.INVALID_STATE, "待确认记录已失效，请刷新任务"
            );
        }
    }

    private AgentTaskEntity toEntity(AgentTask value) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setEnvironmentId(value.environmentId());
        entity.setConversationId(value.conversationId());
        entity.setGoal(value.goal());
        entity.setStatus(value.status().name());
        entity.setCurrentStep(value.currentStep());
        entity.setMaxSteps(value.maxSteps());
        entity.setToolCallCount(value.toolCallCount());
        entity.setReplanCount(value.replanCount());
        entity.setModificationCount(value.modificationCount());
        entity.setPlanJson(toJson(value.plan()));
        entity.setContextJsonRedacted(value.contextJsonRedacted());
        entity.setResultSummary(value.resultSummary());
        entity.setErrorCode(value.errorCode());
        entity.setErrorMessage(value.errorMessage());
        entity.setCancelRequested(value.cancelRequested());
        entity.setLockVersion(value.lockVersion());
        entity.setDeadlineAt(value.deadlineAt());
        entity.setCreatedAt(value.createdAt());
        entity.setStartedAt(value.startedAt());
        entity.setCompletedAt(value.completedAt());
        entity.setUpdatedAt(value.updatedAt());
        return entity;
    }

    private List<String> terminalStatuses() {
        return List.of(
                AgentTaskStatus.SUCCEEDED.name(),
                AgentTaskStatus.NEEDS_REVIEW.name(),
                AgentTaskStatus.FAILED.name(),
                AgentTaskStatus.CANCELLED.name()
        );
    }

    private AgentToolCallEntity toEntity(AgentToolCall value) {
        AgentToolCallEntity entity = new AgentToolCallEntity();
        entity.setId(value.id());
        entity.setTaskId(value.taskId());
        entity.setStepIndex(value.stepIndex());
        entity.setToolName(value.toolName());
        entity.setCallKey(value.callKey());
        entity.setRequestJsonRedacted(value.requestJsonRedacted());
        entity.setResponseJsonRedacted(value.responseJsonRedacted());
        entity.setStatus(value.status().name());
        entity.setAttempt(value.attempt());
        entity.setDurationMs(value.durationMs());
        entity.setErrorCode(value.errorCode());
        entity.setErrorMessage(value.errorMessage());
        entity.setCreatedAt(value.createdAt());
        entity.setCompletedAt(value.completedAt());
        return entity;
    }

    private AgentConfirmationEntity toEntity(AgentConfirmation value) {
        AgentConfirmationEntity entity = new AgentConfirmationEntity();
        entity.setId(value.id());
        entity.setTaskId(value.taskId());
        entity.setStepIndex(value.stepIndex());
        entity.setStatus(value.status().name());
        entity.setRequestJson(value.requestJson());
        entity.setPlanHash(value.planHash());
        entity.setDecisionNote(value.decisionNote());
        entity.setDecidedByUserId(value.decidedByUserId());
        entity.setExpiresAt(value.expiresAt());
        entity.setCreatedAt(value.createdAt());
        entity.setDecidedAt(value.decidedAt());
        return entity;
    }

    private AgentTask toDomain(AgentTaskEntity entity) {
        return new AgentTask(
                entity.getId(),
                entity.getProjectId(),
                entity.getEnvironmentId(),
                entity.getConversationId(),
                entity.getGoal(),
                AgentTaskStatus.valueOf(entity.getStatus()),
                entity.getCurrentStep(),
                entity.getMaxSteps(),
                entity.getToolCallCount(),
                entity.getReplanCount(),
                entity.getModificationCount() == null ? 0 : entity.getModificationCount(),
                fromPlanJson(entity.getPlanJson()),
                entity.getContextJsonRedacted(),
                entity.getResultSummary(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                Boolean.TRUE.equals(entity.getCancelRequested()),
                entity.getLockVersion(),
                entity.getDeadlineAt(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getUpdatedAt()
        );
    }

    private AgentConversation toDomain(AgentConversationEntity entity) {
        return new AgentConversation(
                entity.getId(),
                entity.getProjectId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AgentTaskEvent toDomain(AgentTaskEventEntity entity) {
        return new AgentTaskEvent(
                entity.getId(),
                entity.getTaskId(),
                entity.getSequenceNo(),
                AgentEventType.valueOf(entity.getEventType()),
                AgentTaskStatus.valueOf(entity.getState()),
                entity.getPayloadJson(),
                entity.getCreatedAt()
        );
    }

    private AgentToolCall toDomain(AgentToolCallEntity entity) {
        return new AgentToolCall(
                entity.getId(),
                entity.getTaskId(),
                entity.getStepIndex(),
                entity.getToolName(),
                entity.getCallKey(),
                entity.getRequestJsonRedacted(),
                entity.getResponseJsonRedacted(),
                ToolCallStatus.valueOf(entity.getStatus()),
                entity.getAttempt(),
                entity.getDurationMs(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }

    private AgentModelCall toDomain(AgentModelCallEntity entity) {
        return new AgentModelCall(
                entity.getId(),
                entity.getTaskId(),
                entity.getModelName(),
                entity.getStatus(),
                entity.getAttempt(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getTotalTokens(),
                entity.getDurationMs(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }

    private AgentConfirmation toDomain(AgentConfirmationEntity entity) {
        return new AgentConfirmation(
                entity.getId(),
                entity.getTaskId(),
                entity.getStepIndex(),
                ConfirmationStatus.valueOf(entity.getStatus()),
                entity.getRequestJson(),
                entity.getPlanHash(),
                entity.getDecisionNote(),
                entity.getDecidedByUserId(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getDecidedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 数据序列化失败", exception);
        }
    }

    private List<AgentPlanStep> fromPlanJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AgentPlanStep>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 计划数据损坏", exception);
        }
    }
}
