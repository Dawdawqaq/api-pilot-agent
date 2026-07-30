package com.dochelper.executor.exception;

import com.dochelper.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 受控 API 执行错误码。
 */
public enum ExecutionErrorCode implements ErrorCode {

    ENVIRONMENT_REQUIRED("EXECUTOR_400_001", "项目没有可用的执行环境", HttpStatus.BAD_REQUEST),
    INVALID_STEP("EXECUTOR_400_002", "执行步骤配置无效", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("EXECUTOR_403_001", "当前环境不允许该 HTTP 方法", HttpStatus.FORBIDDEN),
    DANGEROUS_CONFIRMATION_REQUIRED(
            "EXECUTOR_409_001",
            "DELETE 请求必须经过人工确认",
            HttpStatus.CONFLICT
    ),
    TARGET_BLOCKED("EXECUTOR_403_002", "目标地址被 SSRF 安全策略拒绝", HttpStatus.FORBIDDEN),
    VARIABLE_NOT_FOUND("EXECUTOR_422_001", "模板变量不存在", HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_JSON_PATH("EXECUTOR_422_002", "JSONPath 表达式无效或未命中", HttpStatus.UNPROCESSABLE_ENTITY),
    REQUEST_TOO_LARGE("EXECUTOR_413_001", "请求体超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE),
    RESPONSE_TOO_LARGE("EXECUTOR_413_002", "响应体超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE),
    REQUEST_TIMEOUT("EXECUTOR_504_001", "被测接口响应超时", HttpStatus.GATEWAY_TIMEOUT),
    REMOTE_REQUEST_FAILED("EXECUTOR_502_001", "被测接口调用失败", HttpStatus.BAD_GATEWAY),
    EXECUTION_NOT_FOUND("EXECUTOR_404_001", "执行记录不存在", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ExecutionErrorCode(String code, String message, HttpStatus httpStatus) {
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
