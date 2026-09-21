package com.dochelper.agent.application;

import java.util.List;
import java.util.Map;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.secret.application.InMemorySecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 待确认任务在进程内存丢失后必须按原始加密引用恢复，不能使用脱敏计划替代。
 */
class AgentRuntimeRecoveryTest {
    @Test
    void shouldRestoreInitialVariablesOnDemandAfterRegistryRestart() {
        var secrets = new InMemorySecretStore();
        var mapper = new ObjectMapper();
        var before = new AgentRuntimeRegistry(secrets, mapper);
        String reference = before.create(1L, "登录", Map.of("password", "fixture-password"), List.of());
        var task = mock(AgentTask.class);
        when(task.id()).thenReturn(1L);
        when(task.contextJsonRedacted()).thenReturn("{\"runtimeContextRef\":\"" + reference + "\"}");
        var after = new AgentRuntimeRegistry(secrets, mapper);
        assertThat(after.require(task).initialVariables()).containsEntry("password", "fixture-password");
    }

    @Test
    void shouldExplainMissingContextInsteadOfRestoringRedactedData() {
        var task = mock(AgentTask.class);
        when(task.id()).thenReturn(1L);
        when(task.contextJsonRedacted()).thenReturn("{\"runtimeContextRef\":\"missing\"}");
        var registry = new AgentRuntimeRegistry(new InMemorySecretStore(), new ObjectMapper());
        assertThatThrownBy(() -> registry.require(task)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AgentErrorCode.RECOVERY_UNAVAILABLE);
    }
}
