package com.dochelper.agent.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dochelper.agent.api.dto.ConfirmationDecisionRequest;
import com.dochelper.agent.api.dto.ModifyPlanRequest;
import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.*;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.application.SensitiveDataSanitizer;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.*;
import com.dochelper.project.domain.repository.*;
import com.dochelper.secret.application.InMemorySecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证计划版本、明文上下文、持久化失败及确认调度顺序，避免仅测试计数变化。
 */
class AgentPlanModificationTest {
    private static final long PROJECT_ID = 100L;
    private static final long TASK_ID = 200L;
    private static final String PLAN_HASH = "a".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper();
    private AgentTaskRepository repository;
    private AgentRuntimeRegistry runtime;
    private AgentTaskRunner runner;
    private AgentPlanner planner;
    private AgentTaskService service;
    private InMemorySecretStore secrets;

    @BeforeEach
    void setUp() {
        repository = mock(AgentTaskRepository.class);
        runner = mock(AgentTaskRunner.class);
        planner = mock(AgentPlanner.class);
        secrets = new InMemorySecretStore();
        runtime = new AgentRuntimeRegistry(secrets, mapper);
        runtime.create(TASK_ID, "测试目标", Map.of("password", "fixture-secret"), List.of());
        runtime.updatePlan(TASK_ID, List.of(step("原计划", "POST", "/posts")));
        var projects = mock(ApiProjectRepository.class);
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(new ApiProject(
                PROJECT_ID, "[E2E_TEST]", "TEST", "测试", ProjectStatus.ACTIVE, 0L,
                LocalDateTime.now(), LocalDateTime.now())));
        var properties = new AgentProperties(10, 30, 2, 3, 2, Duration.ofMinutes(2),
                Duration.ofMinutes(10), Duration.ofMillis(500), Duration.ofSeconds(30),
                20000, 12, 4, 2, 8);
        var sanitizer = new SensitiveDataSanitizer(new ExecutorProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1048576, 524288, 10, 2,
                Duration.ofMillis(10), "(?i).*(authorization|token|password|secret|cookie).*"));
        service = new AgentTaskService(repository, projects, mock(ProjectEnvironmentRepository.class),
                mock(OpenApiCatalogRepository.class), runtime, runner, planner, new AgentTaskStateMachine(),
                new AgentTaskAdmissionService(repository, properties),
                new SyncTaskExecutor(), sanitizer, properties, mapper);
        when(repository.findTask(PROJECT_ID, TASK_ID)).thenReturn(Optional.of(task(0)));
        when(repository.tryAcquireLease(eq(TASK_ID), anyString(), any(), any())).thenReturn(true);
        when(repository.findConfirmation(TASK_ID, 0)).thenReturn(Optional.of(confirmation(ConfirmationStatus.PENDING)));
        when(repository.decideConfirmation(anyLong(), any(), any(), any(), anyLong(), any())).thenReturn(true);
        when(planner.modify(any())).thenReturn(List.of(step("修改后", "POST", "/posts")));
    }

    @Test
    void shouldPersistAndActivateSameRevisionWithRestorableContext() throws Exception {
        service.modifyPlan(PROJECT_ID, TASK_ID, new ModifyPlanRequest("修改标题"));
        var captor = ArgumentCaptor.forClass(AgentPlanRevision.class);
        verify(repository).revisePendingPlan(captor.capture());
        AgentPlanRevision revision = captor.getValue();
        String reference = mapper.readTree(revision.contextJsonRedacted()).path("runtimeContextRef").asText();
        assertThat(runtime.reference(TASK_ID)).contains(reference);
        assertThat(runtime.find(TASK_ID).orElseThrow().plan().getFirst().objective()).isEqualTo("修改后");
        var restored = new AgentRuntimeRegistry(secrets, mapper);
        assertThat(restored.restore(TASK_ID, reference)).isTrue();
        assertThat(mapper.writeValueAsString(restored.find(TASK_ID).orElseThrow()))
                .isEqualTo(mapper.writeValueAsString(runtime.find(TASK_ID).orElseThrow()));
        assertThat(revision.planJsonRedacted()).doesNotContain("fixture-secret");
        verify(runner).validatePlan(anyList());
    }

    @Test
    void shouldKeepOldRuntimeIfDatabaseRejectsRevision() {
        String originalReference = runtime.reference(TASK_ID).orElseThrow();
        doThrow(new BusinessException(AgentErrorCode.TASK_BUSY)).when(repository).revisePendingPlan(any());
        assertThatThrownBy(() -> service.modifyPlan(PROJECT_ID, TASK_ID, new ModifyPlanRequest("修改")))
                .isInstanceOf(BusinessException.class);
        assertThat(runtime.reference(TASK_ID)).contains(originalReference);
        assertThat(secrets.get(originalReference)).isPresent();
        assertThat(runtime.find(TASK_ID).orElseThrow().plan().getFirst().objective()).isEqualTo("原计划");
    }

    @Test
    void shouldUseRawPlanForModelButRedactStoredPlan() {
        when(planner.modify(any())).thenReturn(List.of(new AgentPlanStep(0, "修改后",
                new ExecutionStepRequest("步骤", "POST", "/posts", Map.of(), Map.of(),
                        Map.of("Authorization", "Bearer fixture-secret"), null, null, List.of(), List.of(), false))));
        service.modifyPlan(PROJECT_ID, TASK_ID, new ModifyPlanRequest("修改"));
        var captor = ArgumentCaptor.forClass(AgentPlanRevision.class);
        verify(repository).revisePendingPlan(captor.capture());
        assertThat(captor.getValue().planJsonRedacted()).doesNotContain("fixture-secret");
        assertThat(runtime.find(TASK_ID).orElseThrow().plan().getFirst().request().headers())
                .containsEntry("Authorization", "Bearer fixture-secret");
    }

    @Test
    void shouldReleaseLeaseBeforeSchedulingApprovedTask() {
        service.confirm(PROJECT_ID, TASK_ID, 0L, new ConfirmationDecisionRequest(true, "同意", PLAN_HASH));
        var order = inOrder(repository, runner);
        order.verify(repository).releaseLease(eq(TASK_ID), anyString());
        order.verify(runner).resumeApproved(PROJECT_ID, TASK_ID);
    }

    @Test
    void shouldRejectStaleBrowserConfirmation() {
        assertThatThrownBy(() -> service.confirm(PROJECT_ID, TASK_ID, 0L,
                new ConfirmationDecisionRequest(true, "同意", "b".repeat(64))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("计划已变化");
        verify(repository, never()).decideConfirmation(anyLong(), any(), any(), any(), anyLong(), any());
        verify(runner, never()).resumeApproved(anyLong(), anyLong());
    }

    @Test
    void shouldRejectModificationAfterApproval() {
        when(repository.findConfirmation(TASK_ID, 0)).thenReturn(Optional.of(confirmation(ConfirmationStatus.APPROVED)));
        assertThatThrownBy(() -> service.modifyPlan(PROJECT_ID, TASK_ID, new ModifyPlanRequest("修改")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(planner);
    }

    @Test
    void shouldRejectFourthModification() {
        when(repository.findTask(PROJECT_ID, TASK_ID)).thenReturn(Optional.of(task(3)));
        assertThatThrownBy(() -> service.modifyPlan(PROJECT_ID, TASK_ID, new ModifyPlanRequest("修改")))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(AgentErrorCode.PLAN_MODIFICATION_LIMIT);
    }

    @Test
    void shouldRejectModificationAndConfirmationWhenBusy() {
        when(repository.tryAcquireLease(eq(TASK_ID), anyString(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.modifyPlan(PROJECT_ID, TASK_ID, new ModifyPlanRequest("修改")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(AgentErrorCode.TASK_BUSY);
        assertThatThrownBy(() -> service.confirm(PROJECT_ID, TASK_ID, 0L,
                new ConfirmationDecisionRequest(true, "同意", PLAN_HASH)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(AgentErrorCode.TASK_BUSY);
    }

    private AgentTask task(int modifications) {
        return new AgentTask(TASK_ID, PROJECT_ID, 1L, 1L, "测试目标", AgentTaskStatus.WAITING_CONFIRMATION,
                0, 10, 0, 0, modifications, List.of(step("脱敏展示", "POST", "/posts")), "{}",
                null, null, null, false, 0, LocalDateTime.now().plusHours(1), LocalDateTime.now(),
                LocalDateTime.now(), null, LocalDateTime.now());
    }

    private AgentConfirmation confirmation(ConfirmationStatus status) {
        return new AgentConfirmation(300L, TASK_ID, 0, status, "{}", PLAN_HASH, null, null,
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now(), null);
    }

    private AgentPlanStep step(String objective, String method, String path) {
        return new AgentPlanStep(0, objective, new ExecutionStepRequest("步骤", method, path,
                Map.of(), Map.of(), Map.of(), null, null, List.of(), List.of(), false));
    }
}
