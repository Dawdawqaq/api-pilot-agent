package com.dochelper.openapi.exception;

import com.dochelper.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * OpenAPI 导入错误码。
 */
public enum OpenApiErrorCode implements ErrorCode {

    EMPTY_FILE("OPENAPI_400_001", "OpenAPI 文件不能为空", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE("OPENAPI_413_001", "OpenAPI 文件超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FILE_TYPE("OPENAPI_400_002", "仅支持 JSON、YAML 和 YML 文件", HttpStatus.BAD_REQUEST),
    INVALID_DOCUMENT("OPENAPI_422_001", "OpenAPI 文档解析失败", HttpStatus.UNPROCESSABLE_ENTITY),
    IMPORT_NOT_FOUND("OPENAPI_404_001", "OpenAPI 导入记录不存在", HttpStatus.NOT_FOUND),
    IMPORT_NOT_RETRYABLE("OPENAPI_409_001", "仅失败的导入记录可以重试", HttpStatus.CONFLICT),
    IMPORT_PROCESSING_FAILED("OPENAPI_500_001", "OpenAPI 导入处理失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    OpenApiErrorCode(String code, String message, HttpStatus httpStatus) {
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
