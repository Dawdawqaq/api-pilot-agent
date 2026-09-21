package com.dochelper.executor.application;

import java.util.List;
import java.util.Set;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.domain.AgentStepExecution;
import com.dochelper.executor.exception.ExecutionErrorCode;
import org.springframework.stereotype.Component;

/**
 * 对无法证明安全的写入恢复采取保守停止，避免重复创建业务资源。
 */
@Component
public class WriteExecutionPolicy {
    public boolean isWrite(ExecutionStepRequest step) {
        return !Set.of("GET", "HEAD", "OPTIONS").contains(step.method().trim().toUpperCase(java.util.Locale.ROOT));
    }

    public void requireFreshWrite(ExecutionStepRequest step, int stepIndex, List<AgentStepExecution> history) {
        if (isWrite(step) && history.stream().anyMatch(item -> item.stepIndex() == stepIndex)) {
            throw new BusinessException(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW);
        }
    }

    public BusinessException classifyFailure(ExecutionStepRequest step, BusinessException failure, boolean receivedResponse) {
        String code = failure.getErrorCode().code();
        if (isWrite(step) && (receivedResponse
                || ExecutionErrorCode.REQUEST_TIMEOUT.code().equals(code)
                || ExecutionErrorCode.REMOTE_REQUEST_FAILED.code().equals(code)
                || ExecutionErrorCode.RESPONSE_TOO_LARGE.code().equals(code))) {
            return new BusinessException(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW,
                    "写步骤未能可靠完成（" + code + "），请求可能已生效。请核验远端结果后重新创建任务，不自动重放。");
        }
        return failure;
    }
}
