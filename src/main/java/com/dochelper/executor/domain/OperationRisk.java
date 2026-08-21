package com.dochelper.executor.domain;

/**
 * 接口操作风险级别。
 */
public enum OperationRisk {
    READ_ONLY,
    SAFE_AUTH,
    MUTATING,
    DESTRUCTIVE;

    /**
     * 判断操作是否需要服务端确认凭据。
     */
    public boolean requiresConfirmation() {
        return this == MUTATING || this == DESTRUCTIVE;
    }
}
