package com.dochelper.agent.application;

import java.util.List;
import java.util.Set;

import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.AgentModelCall;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.governance.application.ModelDataPolicyService;
import com.dochelper.governance.domain.ProjectModelPolicy;
import com.dochelper.model.RuntimeModelProvider;
import com.dochelper.model.domain.LlmConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证离线和真实模型之间热切换，并保留本轮供应商校验和模型审计。 */
class RuntimeAgentPlannerTest {
    @Test void shouldSwitchModelsForNewPlanningCallsAndPreserveGovernance() throws Exception {
        var models = mock(RuntimeModelProvider.class);
        var offline = spy(new DeterministicAgentPlanner());
        var repository = mock(AgentTaskRepository.class);
        var policy = mock(ModelDataPolicyService.class);
        var properties = mock(AgentProperties.class);
        when(properties.maxTotalModelTokens()).thenReturn(20000);
        when(policy.requireAllowed(eq(1L), anyString())).thenReturn(ProjectModelPolicy.defaults(1L, 12));
        var mapper = new ObjectMapper();
        var planner = new RuntimeAgentPlanner(models, offline, mapper, mock(Validator.class), repository,
                policy, properties, mock(PlanningSchemaResolver.class));
        var request = new ExecutionStepRequest("查询", "GET", "/records", null, null, null, null, null, null, null, false);
        var context = new AgentPlanningContext(2L, 1L, "查询记录", List.of(), List.of(), Set.of(), List.of(request));
        var first = mock(ChatModel.class);
        var second = mock(ChatModel.class);
        String plan = mapper.writeValueAsString(List.of(new AgentPlanStep(0, "查询", request)));
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage(plan))));
        when(first.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);
        when(second.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);
        when(models.snapshot()).thenReturn(
                new RuntimeModelProvider.Snapshot(new LlmConfiguration("OFFLINE", "DEEPSEEK", "https://a.example", "model-a", null), first),
                new RuntimeModelProvider.Snapshot(new LlmConfiguration("API", "DEEPSEEK", "https://a.example", "model-a", "ref-a"), first),
                new RuntimeModelProvider.Snapshot(new LlmConfiguration("API", "OPENAI_COMPATIBLE", "https://b.example", "model-b", "ref-b"), second));

        assertThat(planner.plan(context)).hasSize(1);
        verify(offline).plan(context);
        verifyNoInteractions(first);
        assertThat(planner.plan(context)).hasSize(1);
        assertThat(planner.plan(context)).hasSize(1);
        verify(policy).requireAllowed(1L, "DEEPSEEK");
        verify(policy).requireAllowed(1L, "OPENAI_COMPATIBLE");
        var audits = ArgumentCaptor.forClass(AgentModelCall.class);
        verify(repository, times(2)).createModelCall(audits.capture());
        assertThat(audits.getAllValues()).extracting(AgentModelCall::modelName).containsExactly("model-a", "model-b");

        when(policy.requireAllowed(1L, "OPENAI_COMPATIBLE")).thenThrow(new BusinessException(
                com.dochelper.agent.exception.AgentErrorCode.PLANNING_FAILED, "项目策略禁止调用外部模型"));
        assertThatThrownBy(() -> planner.plan(context)).isInstanceOf(BusinessException.class).hasMessageContaining("禁止");
        verify(second, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }
}
