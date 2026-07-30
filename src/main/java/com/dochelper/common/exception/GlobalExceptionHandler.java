package com.dochelper.common.exception;

import java.util.stream.Collectors;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.web.RequestIdWebFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.reactive.resource.NoResourceFoundException;

/**
 * 将框架异常和业务异常转换为稳定的 API 错误协议。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理可预期的业务异常。
     *
     * @param exception 业务异常
     * @param exchange 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            ServerWebExchange exchange
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage(), requestId(exchange)));
    }

    /**
     * 处理请求体参数校验异常。
     *
     * @param exception 参数绑定异常
     * @param exchange 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            WebExchangeBindException exception,
            ServerWebExchange exchange
    ) {
        String message = exception.getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return invalidArgument(message, exchange);
    }

    /**
     * 处理方法参数和请求格式异常。
     *
     * @param exception 请求异常
     * @param exchange 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler({ConstraintViolationException.class, ServerWebInputException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidArgument(
            Exception exception,
            ServerWebExchange exchange
    ) {
        return invalidArgument(exception.getMessage(), exchange);
    }

    /**
     * 处理未匹配到接口或静态资源的请求。
     *
     * @param exception 资源不存在异常
     * @param exchange 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            NoResourceFoundException exception,
            ServerWebExchange exchange
    ) {
        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(ApiResponse.failure(
                        CommonErrorCode.RESOURCE_NOT_FOUND.code(),
                        CommonErrorCode.RESOURCE_NOT_FOUND.message(),
                        requestId(exchange)
                ));
    }

    /**
     * 处理未预期异常，避免向调用方暴露堆栈与内部实现。
     *
     * @param exception 未预期异常
     * @param exchange 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            ServerWebExchange exchange
    ) {
        String requestId = requestId(exchange);
        LOGGER.error("未处理异常，requestId={}", requestId, exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.failure(
                        CommonErrorCode.INTERNAL_ERROR.code(),
                        CommonErrorCode.INTERNAL_ERROR.message(),
                        requestId
                ));
    }

    private ResponseEntity<ApiResponse<Void>> invalidArgument(
            String message,
            ServerWebExchange exchange
    ) {
        return ResponseEntity.status(CommonErrorCode.INVALID_ARGUMENT.httpStatus())
                .body(ApiResponse.failure(
                        CommonErrorCode.INVALID_ARGUMENT.code(),
                        message,
                        requestId(exchange)
                ));
    }

    private String requestId(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE, "unknown");
    }
}
