package com.dochelper.report.application;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentEventType;
import com.dochelper.agent.domain.AgentTaskEvent;
import com.dochelper.agent.domain.AgentToolCall;
import com.dochelper.agent.domain.ToolCallStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.agent.tool.AgentTestReport;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.contract.domain.ContractOperationResult;
import com.dochelper.contract.domain.TestRunCoverage;
import com.dochelper.contract.domain.repository.ContractResultRepository;
import com.dochelper.executor.domain.ExecutionAudit;
import com.dochelper.executor.domain.ExecutionStepAudit;
import com.dochelper.executor.domain.repository.ExecutionAuditRepository;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.report.domain.TestReport;
import com.dochelper.report.domain.TestReportStep;
import com.dochelper.report.domain.repository.TestReportRepository;
import com.dochelper.report.exception.ReportErrorCode;
import org.springframework.stereotype.Service;

/**
 * 从 Agent、RAG 和执行器审计生成持久化测试报告。
 */
@Service
public class TestReportService {

    private static final String EXECUTE_TOOL = "executeHttpRequest";

    private final TestReportRepository reportRepository;
    private final AgentTaskRepository taskRepository;
    private final ExecutionAuditRepository executionRepository;
    private final ApiProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final ContractResultRepository contractResultRepository;
    private final OpenApiCatalogRepository openApiCatalogRepository;

    public TestReportService(
            TestReportRepository reportRepository,
            AgentTaskRepository taskRepository,
            ExecutionAuditRepository executionRepository,
            ApiProjectRepository projectRepository,
            ContractResultRepository contractResultRepository,
            OpenApiCatalogRepository openApiCatalogRepository,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.projectRepository = projectRepository;
        this.contractResultRepository = contractResultRepository;
        this.openApiCatalogRepository = openApiCatalogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 幂等生成任务报告。
     */
    public AgentTestReport generate(Long projectId, Long taskId) {
        return generateInternal(projectId, taskId, null, null, null);
    }

    /**
     * 为失败或取消的任务幂等生成终态报告。
     *
     * @param projectId 项目标识
     * @param taskId 任务标识
     * @param terminalPhase 失败或取消前所处阶段
     * @param errorCode 终态错误码
     * @param errorMessage 终态错误信息
     * @return 标准任务报告
     */
    public AgentTestReport generateTerminal(
            Long projectId,
            Long taskId,
            String terminalPhase,
            String errorCode,
            String errorMessage
    ) {
        return generateInternal(projectId, taskId, terminalPhase, errorCode, errorMessage);
    }

    private AgentTestReport generateInternal(
            Long projectId,
            Long taskId,
            String terminalPhase,
            String terminalErrorCode,
            String terminalErrorMessage
    ) {
        TestReport existing = reportRepository.findByTaskId(taskId).orElse(null);
        if (existing != null) {
            return toToolReport(existing);
        }
        AgentTask task = taskRepository.findTask(projectId, taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TASK_NOT_FOUND));
        List<AgentToolCall> toolCalls = taskRepository.findToolCalls(taskId);
        Optional<Long> executionId = findExecutionId(toolCalls);
        ExecutionAudit execution = executionId
                .flatMap(id -> executionRepository.findByProjectAndId(projectId, id))
                .orElse(null);
        if (execution == null && terminalPhase == null) {
            throw new BusinessException(ReportErrorCode.EXECUTION_NOT_FOUND);
        }
        List<ExecutionStepAudit> executionSteps = execution == null
                ? List.of()
                : executionRepository.findSteps(execution.id());
        int passed = (int) executionSteps.stream().filter(ExecutionStepAudit::success).count();
        int failed = executionSteps.size() - passed;
        int failedToolCalls = (int) toolCalls.stream()
                .filter(call -> call.status() == ToolCallStatus.FAILED
                        || call.status() == ToolCallStatus.REJECTED)
                .count();
        int successfulToolCalls = toolCalls.size() - failedToolCalls;
        PlanningEvidence planningEvidence = extractPlanningEvidence(taskId, task.contextJsonRedacted());
        List<String> citations = planningEvidence.citations();
        LocalDateTime now = LocalDateTime.now();
        Long reportId = IdWorker.getId();
        String reportStatus = terminalPhase == null
                ? execution.status().name()
                : task.status().name();
        String summary = buildSummary(
                task, reportStatus, terminalPhase, terminalErrorCode, terminalErrorMessage,
                executionSteps.size(), passed, failed
        );
        CoverageSummary coverage = execution == null
                ? CoverageSummary.empty()
                : calculateCoverage(projectId, execution.id());
        TestReport report = new TestReport(
                reportId,
                projectId,
                taskId,
                executionId.orElse(null),
                limit(task.goal(), 300),
                reportStatus,
                summary,
                executionSteps.size(),
                passed,
                failed,
                toolCalls.size(),
                execution == null || execution.durationMs() == null
                        ? taskDuration(task)
                        : execution.durationMs(),
                citations,
                toJson(buildMetrics(
                        executionSteps.size(), passed, toolCalls.size(), successfulToolCalls,
                        failedToolCalls, task.replanCount(), planningEvidence, coverage,
                        terminalPhase, terminalErrorCode
                )),
                now
        );
        List<TestReportStep> reportSteps = executionSteps.stream()
                .map(step -> snapshot(reportId, step))
                .toList();
        TestReport created = reportRepository.create(report, reportSteps);
        if (execution != null) {
            contractResultRepository.saveCoverage(new TestRunCoverage(
                    IdWorker.getId(), projectId, created.id(), execution.id(),
                    coverage.operationTotal(), coverage.operationCovered(),
                    coverage.methodTotal(), coverage.methodCovered(),
                    coverage.statusTotal(), coverage.statusCovered(),
                    coverage.schemaTotal(), coverage.schemaCovered(),
                    coverage.uniqueServerErrors(), now
            ));
        }
        return toToolReport(created);
    }

    public List<TestReport> list(Long projectId, int limit) {
        requireProject(projectId);
        return reportRepository.findByProjectId(projectId, limit);
    }

    public TestReport get(Long projectId, Long reportId) {
        requireProject(projectId);
        return reportRepository.findByProjectAndId(projectId, reportId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    public List<TestReportStep> steps(Long projectId, Long reportId) {
        get(projectId, reportId);
        return reportRepository.findSteps(reportId);
    }

    /**
     * 导出标准 JUnit XML，供 GitHub Actions、GitLab CI 与 Jenkins 解析。
     */
    public String exportJUnit(Long projectId, Long reportId) {
        TestReport report = get(projectId, reportId);
        List<TestReportStep> steps = steps(projectId, reportId);
        boolean terminalFailureWithoutStep = Set.of("FAILED", "NEEDS_REVIEW")
                .contains(report.status()) && steps.isEmpty();
        int exportedTests = report.totalSteps() + (terminalFailureWithoutStep ? 1 : 0);
        int exportedFailures = report.failedSteps() + (terminalFailureWithoutStep ? 1 : 0);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"").append(escapeXml(report.title()))
                .append("\" tests=\"").append(exportedTests)
                .append("\" failures=\"").append(exportedFailures)
                .append("\" time=\"").append(report.durationMs() / 1000.0).append("\">\n");
        if (terminalFailureWithoutStep) {
            xml.append("  <testcase name=\"Agent planning\" classname=\"ApiPilot.Agent\" time=\"0\">")
                    .append("<failure message=\"")
                    .append(escapeXml(report.summary()))
                    .append("\"/></testcase>\n");
        }
        for (TestReportStep step : steps) {
            xml.append("  <testcase name=\"").append(escapeXml(step.stepName()))
                    .append("\" classname=\"ApiPilot.").append(escapeXml(step.httpMethod()))
                    .append("\" time=\"").append(step.durationMs() / 1000.0).append("\">");
            if (!step.success()) {
                xml.append("<failure message=\"")
                        .append(escapeXml(step.errorMessage() == null ? "断言或契约失败" : step.errorMessage()))
                        .append("\"/>");
            }
            xml.append("</testcase>\n");
        }
        xml.append("</testsuite>\n");
        return xml.toString();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private Optional<Long> findExecutionId(List<AgentToolCall> calls) {
        return calls.stream()
                .filter(call -> EXECUTE_TOOL.equals(call.toolName()))
                .filter(call -> call.responseJsonRedacted() != null)
                .reduce((first, second) -> second)
                .map(this::executionId)
                .filter(id -> id > 0);
    }

    private Long executionId(AgentToolCall call) {
        try {
            return objectMapper.readTree(call.responseJsonRedacted()).path("id").asLong();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 工具响应 JSON 数据损坏", exception);
        }
    }

    private TestReportStep snapshot(Long reportId, ExecutionStepAudit step) {
        return new TestReportStep(
                IdWorker.getId(),
                reportId,
                step.id(),
                step.stepIndex(),
                step.stepName(),
                step.httpMethod(),
                step.requestUrl(),
                step.requestHeadersJson(),
                step.requestBodyRedacted(),
                step.responseStatus(),
                step.responseHeadersJson(),
                step.responseBodyRedacted(),
                step.assertionsJson(),
                step.success(),
                step.durationMs(),
                step.errorMessage(),
                step.createdAt()
        );
    }

    private String buildSummary(
            AgentTask task,
            String status,
            String terminalPhase,
            String terminalErrorCode,
            String terminalErrorMessage,
            int total,
            int passed,
            int failed
    ) {
        String base = "任务“" + task.goal() + "”执行状态为 " + status
                + "，共 " + total + " 个已执行步骤，通过 " + passed + " 个，失败 " + failed + " 个";
        if (terminalPhase == null) {
            return base;
        }
        return base + "；终止阶段 " + terminalPhase
                + "，错误 " + safeText(terminalErrorCode, "UNKNOWN")
                + "：" + safeText(terminalErrorMessage, "未知错误");
    }

    private Map<String, Object> buildMetrics(
            int totalSteps,
            int passedSteps,
            int toolCallCount,
            int successfulToolCalls,
            int failedToolCalls,
            int replanCount,
            PlanningEvidence planningEvidence,
            CoverageSummary coverage,
            String terminalPhase,
            String terminalErrorCode
    ) {
        Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("successRate", totalSteps == 0 ? 0.0 : (double) passedSteps / totalSteps);
        metrics.put("toolCallCount", toolCallCount);
        metrics.put("successfulToolCalls", successfulToolCalls);
        metrics.put("failedToolCalls", failedToolCalls);
        metrics.put("replanCount", replanCount);
        metrics.put("evidenceCount", planningEvidence.citations().size());
        metrics.put("openApiCandidateCount", planningEvidence.openApiCandidateCount());
        metrics.put("schemaCount", planningEvidence.schemaCount());
        metrics.put("documentEvidenceCount", planningEvidence.documentEvidenceCount());
        if (terminalPhase != null) {
            metrics.put("terminalPhase", terminalPhase);
            metrics.put("errorCode", safeText(terminalErrorCode, "UNKNOWN"));
        }
        metrics.put("operationCoverage", coverage.operationRate());
        metrics.put("methodCoverage", coverage.methodRate());
        metrics.put("statusCodeCoverage", coverage.statusRate());
        metrics.put("schemaRuleCoverage", coverage.schemaRate());
        metrics.put("unique5xxCount", coverage.uniqueServerErrors());
        return metrics;
    }

    private CoverageSummary calculateCoverage(Long projectId, Long executionId) {
        List<ApiEndpoint> endpoints = openApiCatalogRepository.findEndpoints(projectId, null);
        List<ContractOperationResult> results = contractResultRepository.findByExecutionId(executionId);
        int operationTotal = endpoints.size();
        int operationCovered = (int) results.stream().map(ContractOperationResult::endpointId)
                .distinct().count();
        int methodTotal = (int) endpoints.stream().map(ApiEndpoint::httpMethod).distinct().count();
        int methodCovered = (int) results.stream().map(ContractOperationResult::httpMethod)
                .distinct().count();
        int statusTotal = endpoints.stream().mapToInt(this::documentedStatusCount).sum();
        int statusCovered = (int) results.stream()
                .map(result -> result.endpointId() + ":" + result.responseStatus())
                .distinct().count();
        int schemaTotal = results.stream().mapToInt(ContractOperationResult::contractRulesTotal).sum();
        int schemaCovered = results.stream().mapToInt(ContractOperationResult::contractRulesCovered).sum();
        int serverErrors = (int) results.stream()
                .filter(result -> result.responseStatus() >= 500)
                .map(result -> result.endpointId() + ":" + result.responseStatus())
                .distinct().count();
        return new CoverageSummary(
                operationTotal, operationCovered, methodTotal, methodCovered,
                statusTotal, statusCovered, schemaTotal, schemaCovered, serverErrors
        );
    }

    private int documentedStatusCount(ApiEndpoint endpoint) {
        if (endpoint.responsesJson() == null || endpoint.responsesJson().isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readTree(endpoint.responsesJson()).size();
        } catch (JsonProcessingException exception) {
            return 0;
        }
    }

    private record CoverageSummary(
            int operationTotal,
            int operationCovered,
            int methodTotal,
            int methodCovered,
            int statusTotal,
            int statusCovered,
            int schemaTotal,
            int schemaCovered,
            int uniqueServerErrors
    ) {
        private static CoverageSummary empty() {
            return new CoverageSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private double operationRate() { return rate(operationCovered, operationTotal); }
        private double methodRate() { return rate(methodCovered, methodTotal); }
        private double statusRate() { return rate(statusCovered, statusTotal); }
        private double schemaRate() { return rate(schemaCovered, schemaTotal); }
        private double rate(int covered, int total) { return total == 0 ? 0.0 : (double) covered / total; }
    }

    private PlanningEvidence extractPlanningEvidence(Long taskId, String contextJson) {
        java.util.LinkedHashSet<String> citations = new java.util.LinkedHashSet<>();
        int openApiCandidateCount = 0;
        int schemaCount = 0;
        int documentEvidenceCount = 0;

        if (contextJson != null && !contextJson.isBlank()) {
            try {
                JsonNode contextCitations = objectMapper.readTree(contextJson).path("citations");
                if (contextCitations.isArray()) {
                    contextCitations.forEach(value -> {
                        if (!value.asText().isBlank()) {
                            citations.add(value.asText());
                        }
                    });
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Agent 上下文 JSON 数据损坏", exception);
            }
        }

        for (AgentTaskEvent event : taskRepository.findEvents(taskId, 0, 500)) {
            try {
                JsonNode payload = objectMapper.readTree(event.payloadJson());
                if (event.eventType() == AgentEventType.OPENAPI_CANDIDATES_SELECTED) {
                    openApiCandidateCount = payload.path("candidateCount").asInt();
                    JsonNode candidates = payload.path("candidates");
                    if (candidates.isArray()) {
                        candidates.forEach(candidate -> citations.add(
                                "OpenAPI " + candidate.path("method").asText("HTTP")
                                        + " " + candidate.path("path").asText("未知路径")
                                        + " · " + candidate.path("summary").asText("未填写摘要")
                                        + " · score=" + candidate.path("score").asInt()
                        ));
                    }
                } else if (event.eventType() == AgentEventType.SCHEMA_CONTEXT_READY) {
                    schemaCount = payload.path("schemaCount").asInt();
                } else if (event.eventType() == AgentEventType.DOCUMENT_EVIDENCE_RETRIEVED
                        || event.eventType() == AgentEventType.RETRIEVAL_COMPLETED) {
                    documentEvidenceCount = payload.path("resultCount").asInt();
                    JsonNode documentCitations = payload.path("citations");
                    if (documentCitations.isArray()) {
                        documentCitations.forEach(value -> {
                            if (!value.asText().isBlank()) {
                                citations.add(value.asText());
                            }
                        });
                    }
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Agent 事件 JSON 数据损坏", exception);
            }
        }
        return new PlanningEvidence(
                List.copyOf(citations), openApiCandidateCount, schemaCount, documentEvidenceCount
        );
    }

    private long taskDuration(AgentTask task) {
        if (task.startedAt() == null || task.completedAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(task.startedAt(), task.completedAt()).toMillis());
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PlanningEvidence(
            List<String> citations,
            int openApiCandidateCount,
            int schemaCount,
            int documentEvidenceCount
    ) {
    }

    private AgentTestReport toToolReport(TestReport report) {
        JsonNode metrics = parseMetrics(report.metricsJson());
        return new AgentTestReport(
                report.id(),
                report.taskId(),
                report.status(),
                report.summary(),
                report.totalToolCalls(),
                metrics.path("successfulToolCalls").asInt(report.totalToolCalls()),
                metrics.path("failedToolCalls").asInt(0),
                report.totalSteps(),
                report.passedSteps(),
                report.failedSteps(),
                report.evidenceCitations()
        );
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试报告指标序列化失败", exception);
        }
    }

    private JsonNode parseMetrics(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试报告指标 JSON 数据损坏", exception);
        }
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
