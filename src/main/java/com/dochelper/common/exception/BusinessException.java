package com.dochelper.common.exception;

/**
 * 可预期的业务异常。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用错误码定义创建业务异常。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义说明创建业务异常。
     *
     * @param errorCode 错误码
     * @param message 自定义错误说明
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 返回错误码定义。
     *
     * @return 错误码定义
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
