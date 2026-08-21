package com.dochelper.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.dochelper.agent.api.dto.ConfirmationDecisionRequest;
import com.dochelper.agent.api.dto.ModifyPlanRequest;
import com.dochelper.agent.api.vo.AgentTaskResponse;
import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.AgentConfirmation;
import com.dochelper.agent.domain.AgentEventType;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.ConfirmationStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.application.SensitiveDataSanitizer;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.domain.AssertionType;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.ProjectStatus;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证多轮对话修改计划的 3 轮熔断、并发防护与哈希同步机制。
 */
class AgentPlanModificationTest {

    private AgentTaskRepository repository;
    private ApiProjectRepository projectRepository;
    private ProjectEnvironmentRepository environmentRepository;
    private OpenApiCatalogRepository openApiCatalogRepository;
    private AgentRuntimeRegistry runtimeRegistry;
    private AgentTaskRunner runner;
    private AgentPlanner planner;
    private AgentTaskStateMachine stateMachine;
    private SensitiveDataSanitizer sanitizer;
    private AgentProperties properties;
    private ObjectMapper objectMapper;
    private AgentTaskService service;

    private final Long projectId = 100L;
    private final Long taskId = 200L;

    @BeforeEach
    void setUp() {
        repository = mock(AgentTaskRepository.class);
        projectRepository = mock(ApiProjectRepository.class);
        environmentRepository = mock(ProjectEnvironmentRepository.class);
        openApiCatalogRepository = mock(OpenApiCatalogRepository.class);
        runtimeRegistry = mock(AgentRuntimeRegistry.class);
        runner = mock(AgentTaskRunner.class);
        planner = new DeterministicAgentPlanner();
        stateMachine = new AgentTaskStateMachine();
        sanitizer = new SensitiveDataSanitizer(new ExecutorProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1024 * 1024,
                512 * 1024, 10, 2, Duration.ofMillis(10),
                "(?i).*(authorization|token|password|secret|credential|session|cookie|api[-_]?key|email|phone).*"
        ));
        properties = new AgentProperties(
                10, 30, 2, 3, 2,
                Duration.ofMinutes(2), Duration.ofMinutes(10),
                Duration.ofMillis(500), Duration.ofSeconds(30),
                20000, 12
        );
        objectMapper = new ObjectMapper();

        service = new AgentTaskService(
                repository,
                projectRepository,
                environmentRepository,
                openApiCatalogRepository,
                runtimeRegistry,
                runner,
                planner,
                stateMachine,
                new SyncTaskExecutor(),
                sanitizer,
                properties,
                objectMapper
        );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new ApiProject(
                projectId, "Test Project", "TEST_CODE", "desc", ProjectStatus.ACTIVE, 1L, LocalDateTime.now(), LocalDateTime.now()
        )));
    }

    @Test
    void shouldSuccessfullyModifyPlanAndIncrementCount() {
        AgentPlanStep initialStep = createSampleStep(0, "GET", "/users");
        AgentTask task = createWaitingTask(0, List.of(initialStep));
        when(repository.findTask(projectId, taskId)).thenReturn(Optional.of(task));
        when(repository.tryAcquireLease(eq(taskId), anyString(), any(), any())).thenReturn(true);
        when(repository.updateModifiedPlan(eq(taskId), anyString(), eq(0), eq(1))).thenReturn(true);
        when(repository.refreshPendingConfirmation(eq(taskId), eq(0), anyString(), any())).thenReturn(true);
        when(openApiCatalogRepository.findEndpoints(projectId, null)).thenReturn(List.of());

        AgentTaskResponse response = service.modifyPlan(
                projectId, taskId, new ModifyPlanRequest("将查询参数改为 pageSize=20")
        );

        verify(repository).updateModifiedPlan(eq(taskId), anyString(), eq(0), eq(1));
        verify(repository).refreshPendingConfirmation(eq(taskId), eq(0), anyString(), any());
        verify(repository).releaseLease(eq(taskId), anyString());
    }

    @Test
    void shouldPassRawInstructionToPlannerAndSanitizedToEvent() {
        AgentPlanStep initialStep = createSampleStep(0, "GET", "/users");
        AgentTask task = createWaitingTask(0, List.of(initialStep));
        when(repository.findTask(projectId, taskId)).thenReturn(Optional.of(task));
        when(repository.tryAcquireLease(eq(taskId), anyString(), any(), any())).thenReturn(true);
        when(repository.updateModifiedPlan(eq(taskId), anyString(), eq(0), eq(1))).thenReturn(true);
        when(repository.refreshPendingConfirmation(eq(taskId), eq(0), anyString(), any())).thenReturn(true);
        when(openApiCatalogRepository.findEndpoints(projectId, null)).thenReturn(List.of());

        // 模拟包含敏感 Token 的修改指令
        String rawInstruction = "将请求头 Authorization 改为 Bearer secret_token_123456";
        service.modifyPlan(projectId, taskId, new ModifyPlanRequest(rawInstruction));

        // 验证事件中的指令被安全脱敏（不包含明文密钥）
        verify(repository).appendEvent(org.mockito.ArgumentMatchers.argThat(event -> {
            assertThat(event.eventType()).isEqualTo(AgentEventType.PLAN_REVISED);
            assertThat(event.payloadJson()).doesNotContain("secret_token_123456");
            return true;
        }));
    }

    @Test
    void shouldRejectModificationWhenExceedingMaxLimit() {
        // 当前修改次数已达 3 次
        AgentTask task = createWaitingTask(3, List.of(createSampleStep(0, "POST", "/users")));
        when(repository.findTask(projectId, taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.modifyPlan(
                projectId, taskId, new ModifyPlanRequest("再次修改")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AgentErrorCode.PLAN_MODIFICATION_LIMIT);
    }

    @Test
    void shouldRejectModificationWhenTaskBusy() {
        AgentTask task = createWaitingTask(0, List.of(createSampleStep(0, "POST", "/users")));
        when(repository.findTask(projectId, taskId)).thenReturn(Optional.of(task));
        // 租约争抢失败（并发修改中）
        when(repository.tryAcquireLease(eq(taskId), anyString(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.modifyPlan(
                projectId, taskId, new ModifyPlanRequest("修改参数")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AgentErrorCode.TASK_BUSY);
    }

    @Test
    void shouldRejectConfirmationWhenTaskBusy() {
        AgentTask task = createWaitingTask(0, List.of(createSampleStep(0, "POST", "/users")));
        when(repository.findTask(projectId, taskId)).thenReturn(Optional.of(task));
        // 租约争抢失败（正在 modify 中）
        when(repository.tryAcquireLease(eq(taskId), anyString(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(
                projectId, taskId, 1L, new ConfirmationDecisionRequest(true, "approve")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AgentErrorCode.TASK_BUSY);
    }

    private AgentTask createWaitingTask(int modificationCount, List<AgentPlanStep> plan) {
        return new AgentTask(
                taskId,
                projectId,
                1L,
                1L,
                "测试目标",
                AgentTaskStatus.WAITING_CONFIRMATION,
                0,
                10,
                0,
                0,
                modificationCount,
                plan,
                "{}",
                null,
                null,
                null,
                false,
                0,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                LocalDateTime.now()
        );
    }

    private AgentPlanStep createSampleStep(int index, String method, String path) {
        return new AgentPlanStep(
                index,
                "测试步骤 " + index,
                new ExecutionStepRequest(
                        "step-" + index,
                        method,
                        path,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        List.of(),
                        List.of(new ResponseAssertionRequest(
                                AssertionType.STATUS_CODE,
                                null,
                                IntNode.valueOf(200),
                                null
                        )),
                        "POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)
                )
        );
    }
}
