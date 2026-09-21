package com.dochelper.agent.application;

import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Agent 状态机拒绝跳步和终态回退。
 */
class AgentTaskStateMachineTest {

    private final AgentTaskStateMachine stateMachine = new AgentTaskStateMachine();

    @Test
    void shouldAllowExpectedReactFlow() {
        assertThatCode(() -> {
            stateMachine.assertTransition(
                    AgentTaskStatus.RECEIVED,
                    AgentTaskStatus.RETRIEVING
            );
            stateMachine.assertTransition(
                    AgentTaskStatus.OBSERVING,
                    AgentTaskStatus.REPLANNING
            );
            stateMachine.assertTransition(
                    AgentTaskStatus.REPORTING,
                    AgentTaskStatus.SUCCEEDED
            );
            stateMachine.assertTransition(
                    AgentTaskStatus.EXECUTING,
                    AgentTaskStatus.NEEDS_REVIEW
            );
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSkippedAndTerminalTransitions() {
        assertThatThrownBy(() -> stateMachine.assertTransition(
                AgentTaskStatus.RECEIVED,
                AgentTaskStatus.EXECUTING
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stateMachine.assertTransition(
                AgentTaskStatus.SUCCEEDED,
                AgentTaskStatus.EXECUTING
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stateMachine.assertTransition(
                AgentTaskStatus.NEEDS_REVIEW,
                AgentTaskStatus.EXECUTING
        )).isInstanceOf(BusinessException.class);
    }
}
