package com.dochelper.report.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dochelper.agent.domain.AgentEventType;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskEvent;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.contract.domain.repository.ContractResultRepository;
import com.dochelper.executor.domain.repository.ExecutionAuditRepository;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.report.domain.TestReport;
import com.dochelper.report.domain.repository.TestReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证任务在 HTTP 执行前失败时仍能生成带规划依据的标准报告。
 */
class TestReportServiceTest {

    @Test
    void shouldGenerateFailureReportWithoutExecutionAudit() throws Exception {
        TestReportRepository reportRepository = mock(TestReportRepository.class);
        AgentTaskRepository taskRepository = mock(AgentTaskRepository.class);
        ExecutionAuditRepository executionRepository = mock(ExecutionAuditRepository.class);
        ContractResultRepository contractRepository = mock(ContractResultRepository.class);
        AgentTask task = mock(AgentTask.class);
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(2);
        LocalDateTime completedAt = LocalDateTime.now();

        when(reportRepository.findByTaskId(2L)).thenReturn(Optional.empty());
        when(reportRepository.create(any(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.findTask(1L, 2L)).thenReturn(Optional.of(task));
        when(taskRepository.findToolCalls(2L)).thenReturn(List.of());
        when(taskRepository.findEvents(2L, 0, 500)).thenReturn(List.of(
                event(1, AgentEventType.OPENAPI_CANDIDATES_SELECTED, """
                        {"candidateCount":1,"candidates":[{"method":"GET","path":"/api/posts",
                        "summary":"分页获取帖子","score":45}]}
                        """),
                event(2, AgentEventType.SCHEMA_CONTEXT_READY,
                        "{\"endpointCount\":1,\"schemaCount\":3}"),
                event(3, AgentEventType.DOCUMENT_EVIDENCE_RETRIEVED,
                        "{\"enabled\":false,\"resultCount\":0,\"citations\":[]}")
        ));
        when(task.goal()).thenReturn("查询帖子列表");
        when(task.status()).thenReturn(AgentTaskStatus.FAILED);
        when(task.contextJsonRedacted()).thenReturn("{}");
        when(task.startedAt()).thenReturn(startedAt);
        when(task.completedAt()).thenReturn(completedAt);

        TestReportService service = new TestReportService(
                reportRepository,
                taskRepository,
                executionRepository,
                mock(ApiProjectRepository.class),
                contractRepository,
                mock(OpenApiCatalogRepository.class),
                new ObjectMapper()
        );

        var result = service.generateTerminal(
                1L, 2L, "PLANNING", "AGENT_400_001", "模型没有返回合法计划"
        );

        ArgumentCaptor<TestReport> reportCaptor = ArgumentCaptor.forClass(TestReport.class);
        verify(reportRepository).create(reportCaptor.capture(), eq(List.of()));
        TestReport report = reportCaptor.getValue();
        assertThat(report.executionId()).isNull();
        assertThat(report.status()).isEqualTo("FAILED");
        assertThat(report.summary()).contains("终止阶段 PLANNING", "AGENT_400_001");
        assertThat(report.evidenceCitations()).containsExactly(
                "OpenAPI GET /api/posts · 分页获取帖子 · score=45"
        );
        var metrics = new ObjectMapper().readTree(report.metricsJson());
        assertThat(metrics.path("openApiCandidateCount").asInt()).isEqualTo(1);
        assertThat(metrics.path("schemaCount").asInt()).isEqualTo(3);
        assertThat(metrics.path("documentEvidenceCount").asInt()).isZero();
        assertThat(metrics.path("terminalPhase").asText()).isEqualTo("PLANNING");
        assertThat(result.status()).isEqualTo("FAILED");
        verifyNoInteractions(executionRepository, contractRepository);
    }

    private AgentTaskEvent event(long sequence, AgentEventType type, String payload) {
        return new AgentTaskEvent(
                sequence, 2L, sequence, type, AgentTaskStatus.PLANNING,
                payload, LocalDateTime.now()
        );
    }
}
