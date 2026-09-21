package com.dochelper.agent.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Agent 任务状态迁移规则。
 */
@Component
public class AgentTaskStateMachine {

    private final Map<AgentTaskStatus, Set<AgentTaskStatus>> transitions =
            new EnumMap<>(AgentTaskStatus.class);

    public AgentTaskStateMachine() {
        allow(AgentTaskStatus.RECEIVED, AgentTaskStatus.RETRIEVING);
        allow(AgentTaskStatus.RETRIEVING, AgentTaskStatus.PLANNING);
        allow(
                AgentTaskStatus.PLANNING,
                AgentTaskStatus.WAITING_CONFIRMATION,
                AgentTaskStatus.EXECUTING
        );
        allow(AgentTaskStatus.WAITING_CONFIRMATION, AgentTaskStatus.EXECUTING);
        allow(AgentTaskStatus.EXECUTING, AgentTaskStatus.OBSERVING);
        allow(
                AgentTaskStatus.OBSERVING,
                AgentTaskStatus.REPLANNING,
                AgentTaskStatus.REPORTING
        );
        allow(
                AgentTaskStatus.REPLANNING,
                AgentTaskStatus.EXECUTING,
                AgentTaskStatus.WAITING_CONFIRMATION
        );
        allow(AgentTaskStatus.REPORTING, AgentTaskStatus.SUCCEEDED);
        for (AgentTaskStatus status : AgentTaskStatus.values()) {
            if (!status.terminal()) {
                transitions.computeIfAbsent(status, ignored ->
                                EnumSet.noneOf(AgentTaskStatus.class))
                        .addAll(EnumSet.of(
                                AgentTaskStatus.NEEDS_REVIEW,
                                AgentTaskStatus.FAILED,
                                AgentTaskStatus.CANCELLED
                        ));
            }
        }
    }

    /**
     * 校验状态迁移是否合法。
     *
     * @param current 当前状态
     * @param next 目标状态
     */
    public void assertTransition(AgentTaskStatus current, AgentTaskStatus next) {
        if (!transitions.getOrDefault(current, Set.of()).contains(next)) {
            throw new BusinessException(
                    AgentErrorCode.INVALID_STATE,
                    "不允许从 " + current + " 迁移到 " + next
            );
        }
    }

    private void allow(AgentTaskStatus source, AgentTaskStatus... targets) {
        transitions.put(source, EnumSet.copyOf(java.util.List.of(targets)));
    }
}
