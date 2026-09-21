package com.dochelper.agent.application;

import java.util.ArrayList;
import java.util.List;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 线程池排队期间反复扫描同一过期任务，不应重复提交恢复工作。
 */
class AgentRecoverySchedulingTest {
    @Test
    void shouldDeduplicateQueuedRecoveryAndReleaseAfterFailure() {
        var repository = mock(AgentTaskRepository.class);
        var runner = mock(AgentTaskRunner.class);
        var task = mock(AgentTask.class);
        when(task.id()).thenReturn(2L);
        when(task.projectId()).thenReturn(1L);
        when(task.status()).thenReturn(AgentTaskStatus.EXECUTING);
        when(repository.findRecoverableTasks(any(), anyInt())).thenReturn(List.of(task));
        var queued = new ArrayList<Runnable>();
        var recovery = new AgentTaskRecoveryService(repository, runner, queued::add);
        recovery.recover();
        recovery.recover();
        assertThat(queued).hasSize(1);
        doThrow(new IllegalStateException("测试恢复失败")).when(runner).resumeRecovered(1L, 2L);
        assertThatThrownBy(queued.getFirst()::run).isInstanceOf(IllegalStateException.class);
        recovery.recover();
        assertThat(queued).hasSize(2);
    }
}
