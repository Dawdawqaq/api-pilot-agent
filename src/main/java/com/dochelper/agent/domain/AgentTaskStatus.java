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
    FAILED,
    CANCELLED;

    /**
     * 判断当前状态是否已经终止。
     *
     * @return 是否为终态
     */
    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
