package com.dochelper.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 通用错误码。
 */
public enum CommonErrorCode implements ErrorCode {

    INVALID_ARGUMENT("COMMON_400_001", "请求参数不合法", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("COMMON_404_001", "请求的资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("COMMON_500_001", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
