package com.dochelper.executor.domain;

/**
 * 执行失败分类，用于区分重试、重规划与终止。
 */
public enum ExecutionErrorCategory {
    CONNECTION,
    TIMEOUT,
    RESPONSE_TOO_LARGE,
    ASSERTION,
    SERVER_5XX,
    SECURITY_POLICY,
    INVALID_REQUEST,
    UNKNOWN
}
