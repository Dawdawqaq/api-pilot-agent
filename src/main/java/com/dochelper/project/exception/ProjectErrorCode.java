package com.dochelper.project.exception;

import com.dochelper.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 项目管理错误码。
 */
public enum ProjectErrorCode implements ErrorCode {

    PROJECT_NOT_FOUND("PROJECT_404_001", "被测项目不存在", HttpStatus.NOT_FOUND),
    PROJECT_CODE_CONFLICT("PROJECT_409_001", "项目编码已存在", HttpStatus.CONFLICT),
    ENVIRONMENT_NOT_FOUND("PROJECT_404_002", "项目环境不存在", HttpStatus.NOT_FOUND),
    ENVIRONMENT_NAME_CONFLICT("PROJECT_409_002", "项目环境名称已存在", HttpStatus.CONFLICT),
    INVALID_BASE_URL("PROJECT_400_001", "Base URL 必须是合法的 HTTP 或 HTTPS 地址", HttpStatus.BAD_REQUEST),
    INVALID_EXECUTION_POLICY(
            "PROJECT_400_002",
            "环境执行策略包含不支持的 HTTP 方法",
            HttpStatus.BAD_REQUEST
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ProjectErrorCode(String code, String message, HttpStatus httpStatus) {
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
