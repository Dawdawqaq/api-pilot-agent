package com.dochelper.agent.application;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证任务准入同时约束项目与全局容量，并在拒绝时不创建任务。
 */
class AgentTaskAdmissionServiceTest {

    private final AgentTaskRepository repository = mock(AgentTaskRepository.class);
    private final AgentTaskAdmissionService service = new AgentTaskAdmissionService(
            repository,
            new AgentProperties(
                    10, 30, 2, 3, 2,
                    Duration.ofMinutes(15), Duration.ofMinutes(10), Duration.ofMillis(500),
                    Duration.ofSeconds(30), 20000, 12, 4, 2, 8
            )
    );

    @Test
    void shouldAdmitWhenBothCapacityLimitsHaveRoom() {
        when(repository.countActiveTasks(1L)).thenReturn(1L);
        when(repository.countActiveTasks()).thenReturn(3L);

        assertThat(service.admit(1L, () -> "created")).isEqualTo("created");
    }

    @Test
    void shouldRejectProjectOverflowBeforeCreatingTask() {
        when(repository.countActiveTasks(1L)).thenReturn(2L);
        AtomicInteger creations = new AtomicInteger();

        assertThatThrownBy(() -> service.admit(1L, () -> {
            creations.incrementAndGet();
            return "created";
        })).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AgentErrorCode.TASK_CAPACITY_EXCEEDED);
        assertThat(creations).hasValue(0);
    }

    @Test
    void shouldRejectGlobalOverflowBeforeCreatingTask() {
        when(repository.countActiveTasks(1L)).thenReturn(0L);
        when(repository.countActiveTasks()).thenReturn(4L);

        assertThatThrownBy(() -> service.admit(1L, () -> "created"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AgentErrorCode.TASK_CAPACITY_EXCEEDED);
    }
}
