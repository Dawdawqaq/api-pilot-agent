package com.dochelper.evaluation.application;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;
import com.dochelper.evaluation.api.dto.RecordQualityEvaluationRequest;
import com.dochelper.evaluation.domain.QualityEvaluationRun;
import com.dochelper.evaluation.domain.repository.QualityEvaluationRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 持久化可追溯的最终质量评测结果。
 */
@Service
public class QualityEvaluationService {

    private final QualityEvaluationRunRepository repository;
    private final ObjectMapper objectMapper;

    public QualityEvaluationService(
            QualityEvaluationRunRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public QualityEvaluationRun record(RecordQualityEvaluationRequest request) {
        validateCounts(request);
        return repository.save(new QualityEvaluationRun(
                IdWorker.getId(), request.datasetVersion(), request.serviceCount(),
                request.evaluationCaseCount(), request.securityCaseCount(),
                request.passedCaseCount(), request.blockedAttackCount(),
                request.taskSuccessRate(), request.validPlanRate(), request.securityBlockRate(),
                request.p95TaskDurationMs(), request.totalModelTokens(),
                toJson(request.metrics()), LocalDateTime.now()
        ));
    }

    public List<QualityEvaluationRun> list(int limit) {
        return repository.findLatest(limit);
    }

    private void validateCounts(RecordQualityEvaluationRequest request) {
        if (request.passedCaseCount() > request.evaluationCaseCount()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "通过用例数不能超过评测用例数");
        }
        if (request.blockedAttackCount() > request.securityCaseCount()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "拦截攻击数不能超过安全用例数");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("质量评测指标序列化失败", exception);
        }
    }

}
