package com.dochelper.executor.domain;

/**
 * 响应断言类型。
 */
public enum AssertionType {
    STATUS_CODE,
    FIELD_EXISTS,
    FIELD_NOT_EXISTS,
    FIELD_TYPE,
    FIELD_EQUALS,
    FIELD_CONTAINS
}
