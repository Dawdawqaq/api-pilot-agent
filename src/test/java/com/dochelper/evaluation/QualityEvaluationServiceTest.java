package com.dochelper.evaluation;

import java.util.Map;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.evaluation.api.dto.RecordQualityEvaluationRequest;
import com.dochelper.evaluation.application.QualityEvaluationService;
import com.dochelper.evaluation.domain.QualityEvaluationRun;
import com.dochelper.evaluation.domain.repository.QualityEvaluationRunRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 质量评测服务测试。
 */
class QualityEvaluationServiceTest {

    @Test
    void shouldPersistValidatedEvaluationRun() {
        QualityEvaluationRunRepository repository = mock(QualityEvaluationRunRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        QualityEvaluationService service = new QualityEvaluationService(
                repository, JsonMapper.builder().build()
        );

        QualityEvaluationRun result = service.record(request(42, 15));

        assertThat(result.datasetVersion()).isEqualTo("evaluation-v1");
        assertThat(result.metricsJson()).contains("unit-test");
        verify(repository).save(any());
    }

    @Test
    void shouldRejectImpossibleEvaluationCounts() {
        QualityEvaluationRunRepository repository = mock(QualityEvaluationRunRepository.class);
        QualityEvaluationService service = new QualityEvaluationService(
                repository, JsonMapper.builder().build()
        );

        assertThatThrownBy(() -> service.record(request(51, 15)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通过用例数不能超过评测用例数");
        verify(repository, never()).save(any());
    }

    private RecordQualityEvaluationRequest request(int passedCaseCount, int blockedAttackCount) {
        return new RecordQualityEvaluationRequest(
                "evaluation-v1", 3, 50, 15, passedCaseCount, blockedAttackCount,
                0.84, 0.90, 1.0, 1250, 6400, Map.of("source", "unit-test")
        );
    }
}
