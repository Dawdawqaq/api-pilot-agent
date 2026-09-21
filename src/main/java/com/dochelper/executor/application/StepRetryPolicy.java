package com.dochelper.executor.application;

import java.util.Locale;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.exception.ExecutionErrorCode;
import org.springframework.stereotype.Component;

/**
 * 集中判断单步骤能否安全重试，并计算指数退避时间。
 */
@Component
public class StepRetryPolicy {

    private final ExecutorProperties properties;

    public StepRetryPolicy(ExecutorProperties properties) {
        this.properties = properties;
    }

    /**
     * 仅允许只读请求在连接失败或超时后重试，模型提供幂等请求头不等于服务端承诺幂等。
     */
    public boolean shouldRetry(ExecutionStepRequest step, String errorCode, int currentAttempt) {
        if (currentAttempt > properties.maxStepRetries()) {
            return false;
        }
        if (!ExecutionErrorCode.REMOTE_REQUEST_FAILED.code().equals(errorCode)
                && !ExecutionErrorCode.REQUEST_TIMEOUT.code().equals(errorCode)) {
            return false;
        }
        String method = step.method().trim().toUpperCase(Locale.ROOT);
        return "GET".equals(method)
                || "HEAD".equals(method);
    }

    /**
     * 按尝试次数执行有上限的指数退避。
     */
    public void pause(int attempt) {
        try {
            long multiplier = 1L << Math.min(4, Math.max(0, attempt - 1));
            Thread.sleep(Math.min(2000, properties.retryBackoff().toMillis() * multiplier));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ExecutionErrorCode.REMOTE_REQUEST_FAILED,
                    "步骤重试等待被中断"
            );
        }
    }

}
