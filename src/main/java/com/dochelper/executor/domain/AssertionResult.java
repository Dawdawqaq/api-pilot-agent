package com.dochelper.executor.domain;

/**
 * 单条响应断言结果。
 */
public record AssertionResult(
        AssertionType type,
        String jsonPath,
        boolean passed,
        String expected,
        String actual,
        String message
) {
}
