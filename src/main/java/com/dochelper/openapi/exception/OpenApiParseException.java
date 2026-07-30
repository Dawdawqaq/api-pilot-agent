package com.dochelper.openapi.exception;

/**
 * OpenAPI 文档解析异常。
 */
public class OpenApiParseException extends RuntimeException {

    public OpenApiParseException(String message) {
        super(message);
    }

    public OpenApiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
