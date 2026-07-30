package com.dochelper.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码契约。
 */
public interface ErrorCode {

    /**
     * 返回稳定的业务错误码。
     *
     * @return 业务错误码
     */
    String code();

    /**
     * 返回面向调用方的错误说明。
     *
     * @return 错误说明
     */
    String message();

    /**
     * 返回对应的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    HttpStatus httpStatus();
}
