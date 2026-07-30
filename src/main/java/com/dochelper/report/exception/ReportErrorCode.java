package com.dochelper.report.exception;

import com.dochelper.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 测试报告模块错误码。
 */
public enum ReportErrorCode implements ErrorCode {
    REPORT_NOT_FOUND("REPORT_404_001", "测试报告不存在", HttpStatus.NOT_FOUND),
    EXECUTION_NOT_FOUND("REPORT_422_001", "任务没有可关联的执行记录", HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ReportErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
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
        return status;
    }
}
