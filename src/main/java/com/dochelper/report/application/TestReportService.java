package com.dochelper.report.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.domain.AgentTask;
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
        TestReport existing = reportRepository.findByTaskId(taskId).orElse(null);
        if (existing != null) {
            return toToolReport(existing);
        }
        AgentTask task = taskRepository.findTask(projectId, taskId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TASK_NOT_FOUND));
        List<AgentToolCall> toolCalls = taskRepository.findToolCalls(taskId);
        Long executionId = findExecutionId(toolCalls);
        ExecutionAudit execution = executionRepository
                .findByProjectAndId(projectId, executionId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.EXECUTION_NOT_FOUND));
        List<ExecutionStepAudit> executionSteps = executionRepository.findSteps(executionId);
        int passed = (int) executionSteps.stream().filter(ExecutionStepAudit::success).count();
        int failed = executionSteps.size() - passed;
        int failedToolCalls = (int) toolCalls.stream()
                .filter(call -> call.status() == ToolCallStatus.FAILED
                        || call.status() == ToolCallStatus.REJECTED)
                .count();
        int successfulToolCalls = toolCalls.size() - failedToolCalls;
        List<String> citations = extractCitations(task.contextJsonRedacted());
        LocalDateTime now = LocalDateTime.now();
        Long reportId = IdWorker.getId();
        String summary = buildSummary(task, execution, executionSteps.size(), passed, failed);
        CoverageSummary coverage = calculateCoverage(projectId, executionId);
        TestReport report = new TestReport(
                reportId,
                projectId,
                taskId,
                executionId,
                limit(task.goal(), 300),
                execution.status().name(),
                summary,
                executionSteps.size(),
                passed,
                failed,
                toolCalls.size(),
                execution.durationMs() == null ? 0 : execution.durationMs(),
                citations,
                toJson(buildMetrics(
                        executionSteps.size(), passed, toolCalls.size(), successfulToolCalls,
                        failedToolCalls, task.replanCount(), citations.size(), coverage
                )),
                now
        );
        List<TestReportStep> reportSteps = executionSteps.stream()
                .map(step -> snapshot(reportId, step))
                .toList();
        TestReport created = reportRepository.create(report, reportSteps);
        contractResultRepository.saveCoverage(new TestRunCoverage(
                IdWorker.getId(), projectId, created.id(), executionId,
                coverage.operationTotal(), coverage.operationCovered(),
                coverage.methodTotal(), coverage.methodCovered(),
                coverage.statusTotal(), coverage.statusCovered(),
                coverage.schemaTotal(), coverage.schemaCovered(),
                coverage.uniqueServerErrors(), now
        ));
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
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"").append(escapeXml(report.title()))
                .append("\" tests=\"").append(report.totalSteps())
                .append("\" failures=\"").append(report.failedSteps())
                .append("\" time=\"").append(report.durationMs() / 1000.0).append("\">\n");
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

    private Long findExecutionId(List<AgentToolCall> calls) {
        return calls.stream()
                .filter(call -> EXECUTE_TOOL.equals(call.toolName()))
                .filter(call -> call.responseJsonRedacted() != null)
                .reduce((first, second) -> second)
                .map(this::executionId)
                .filter(id -> id > 0)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.EXECUTION_NOT_FOUND));
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
            ExecutionAudit execution,
            int total,
            int passed,
            int failed
    ) {
        return "任务“" + task.goal() + "”执行状态为 " + execution.status()
                + "，共 " + total + " 个步骤，通过 " + passed + " 个，失败 " + failed + " 个";
    }

    private Map<String, Object> buildMetrics(
            int totalSteps,
            int passedSteps,
            int toolCallCount,
            int successfulToolCalls,
            int failedToolCalls,
            int replanCount,
            int evidenceCount,
            CoverageSummary coverage
    ) {
        Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("successRate", totalSteps == 0 ? 0.0 : (double) passedSteps / totalSteps);
        metrics.put("toolCallCount", toolCallCount);
        metrics.put("successfulToolCalls", successfulToolCalls);
        metrics.put("failedToolCalls", failedToolCalls);
        metrics.put("replanCount", replanCount);
        metrics.put("evidenceCount", evidenceCount);
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
        private double operationRate() { return rate(operationCovered, operationTotal); }
        private double methodRate() { return rate(methodCovered, methodTotal); }
        private double statusRate() { return rate(statusCovered, statusTotal); }
        private double schemaRate() { return rate(schemaCovered, schemaTotal); }
        private double rate(int covered, int total) { return total == 0 ? 0.0 : (double) covered / total; }
    }

    private List<String> extractCitations(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode citations = objectMapper.readTree(contextJson).path("citations");
            if (!citations.isArray()) {
                return List.of();
            }
            return java.util.stream.StreamSupport.stream(citations.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 上下文 JSON 数据损坏", exception);
        }
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
