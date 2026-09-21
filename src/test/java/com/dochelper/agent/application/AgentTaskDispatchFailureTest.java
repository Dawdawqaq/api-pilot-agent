package com.dochelper.agent.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dochelper.agent.api.dto.CreateAgentTaskRequest;
import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.application.SensitiveDataSanitizer;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.ProjectEnvironment;
import com.dochelper.project.domain.ProjectStatus;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.secret.application.InMemorySecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证线程池拒绝任务时不会留下静默等待的已接收任务。
 */
class AgentTaskDispatchFailureTest {

    @Test
    void shouldPersistTerminalFailureAndReturnCapacityErrorWhenQueueRejects() {
        AgentTaskRepository repository = mock(AgentTaskRepository.class);
        ApiProjectRepository projects = mock(ApiProjectRepository.class);
        ProjectEnvironmentRepository environments = mock(ProjectEnvironmentRepository.class);
        AgentTaskRunner runner = mock(AgentTaskRunner.class);
        ObjectMapper mapper = new ObjectMapper();
        AgentProperties properties = new AgentProperties(
                10, 30, 2, 3, 2,
                Duration.ofMinutes(15), Duration.ofMinutes(10), Duration.ofMillis(500),
                Duration.ofSeconds(30), 20000, 12, 4, 2, 8
        );
        LocalDateTime now = LocalDateTime.now();
        when(projects.findById(1L)).thenReturn(Optional.of(new ApiProject(
                1L, "test", "测试", "测试", ProjectStatus.ACTIVE, 0L, now, now
        )));
        when(environments.findByIdAndProjectId(2L, 1L)).thenReturn(Optional.of(new ProjectEnvironment(
                2L, 1L, "local", "http://127.0.0.1:8080", "GET", true, true, now, now
        )));
        when(repository.createConversation(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.createTask(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TaskExecutor rejectingExecutor = command -> {
            throw new TaskRejectedException("测试队列已满");
        };
        SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(new ExecutorProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1048576, 524288, 10, 2,
                Duration.ofMillis(10), "(?i).*(authorization|token|password|secret|cookie).*"
        ));
        AgentTaskService service = new AgentTaskService(
                repository, projects, environments, mock(OpenApiCatalogRepository.class),
                new AgentRuntimeRegistry(new InMemorySecretStore(), mapper), runner,
                mock(AgentPlanner.class), new AgentTaskStateMachine(),
                new AgentTaskAdmissionService(repository, properties), rejectingExecutor,
                sanitizer, properties, mapper
        );

        assertThatThrownBy(() -> service.create(
                1L, new CreateAgentTaskRequest(2L, null, "查询健康状态", Map.of(), List.of())
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AgentErrorCode.TASK_CAPACITY_EXCEEDED);
        verify(runner).failDispatch(eq(1L), anyLong(), any(BusinessException.class));
    }
}
