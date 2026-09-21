package com.dochelper.agent.domain;

/**
 * Agent 任务生命周期状态。
 */
public enum AgentTaskStatus {
    RECEIVED,
    RETRIEVING,
    PLANNING,
    WAITING_CONFIRMATION,
    EXECUTING,
    OBSERVING,
    REPLANNING,
    REPORTING,
    SUCCEEDED,
    NEEDS_REVIEW,
    FAILED,
    CANCELLED;

    /**
     * 判断当前状态是否已经终止。
     *
     * @return 是否为终态
     */
    public boolean terminal() {
        return this == SUCCEEDED || this == NEEDS_REVIEW || this == FAILED || this == CANCELLED;
    }
}
