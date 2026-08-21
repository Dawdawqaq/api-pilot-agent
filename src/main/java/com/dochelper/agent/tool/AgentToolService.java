package com.dochelper.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecuteScenarioRequest;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.api.dto.VariableExtractorRequest;
import com.dochelper.executor.application.JsonPathResponseProcessor;
import com.dochelper.executor.application.ScenarioExecutionService;
import com.dochelper.executor.domain.AssertionResult;
import com.dochelper.executor.domain.ScenarioExecutionResult;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.retrieval.application.HybridRetrievalService;
import com.dochelper.retrieval.domain.RetrievalResult;
import com.dochelper.report.application.TestReportService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Agent 可调用的六个强类型业务工具。
 *
 * <p>工具不暴露数据库、文件系统或任意 URL，HTTP 调用只能委托阶段 4
 * 的受控执行器完成。</p>
 */
@Component
public class AgentToolService {

    private final HybridRetrievalService retrievalService;
    private final OpenApiCatalogRepository openApiRepository;
    private final ScenarioExecutionService executionService;
    private final JsonPathResponseProcessor responseProcessor;
    private final TestReportService reportService;

    public AgentToolService(
            HybridRetrievalService retrievalService,
            OpenApiCatalogRepository openApiRepository,
            ScenarioExecutionService executionService,
            JsonPathResponseProcessor responseProcessor,
            TestReportService reportService
    ) {
        this.retrievalService = retrievalService;
        this.openApiRepository = openApiRepository;
        this.executionService = executionService;
        this.responseProcessor = responseProcessor;
        this.reportService = reportService;
    }

    /**
     * 检索接口定义、错误码和业务规则。
     */
    @Tool(description = "检索指定项目的接口文档、错误码和业务规则，并返回带来源引用的结果")
    public List<RetrievalResult> searchApiDocument(
            Long projectId,
            String query,
            Integer topK
    ) {
        return retrievalService.search(projectId, query, topK == null ? 5 : topK);
    }

    /**
     * 获取指定接口的结构化 Schema。
     */
    @Tool(description = "根据项目标识和接口标识获取 OpenAPI 请求参数、请求体、响应及认证结构")
    public ApiEndpoint getApiSchema(Long projectId, Long endpointId) {
        return openApiRepository.findEndpoint(projectId, endpointId)
                .orElseThrow(() -> new BusinessException(
                        AgentErrorCode.INVALID_PLAN,
                        "计划引用的接口不存在"
                ));
    }

    /**
     * 通过安全执行器执行多步骤 HTTP 请求。
     */
    @Tool(description = "通过受控执行器执行 HTTP 请求，自动应用域名、方法、超时、大小和脱敏限制")
    public ScenarioExecutionResult executeHttpRequest(
            Long projectId,
            ExecuteScenarioRequest request
    ) {
        return executionService.execute(projectId, request);
    }

    /**
     * 供 Agent 主循环使用的服务端确认执行入口。
     */
    public ScenarioExecutionResult executeConfirmedHttpRequest(
            Long taskId,
            Long projectId,
            ExecuteScenarioRequest request,
            Set<Integer> confirmedStepIndexes
    ) {
        return executionService.executeForAgent(
                taskId, projectId, request, confirmedStepIndexes
        );
    }

    /**
     * 使用 JSONPath 提取响应值。
     */
    @Tool(description = "使用 JSONPath 从结构化响应体中提取后续步骤变量")
    public Map<String, Object> extractResponseValue(
            JsonNode responseBody,
            List<VariableExtractorRequest> extractors
    ) {
        return responseProcessor.extract(responseBody, extractors);
    }

    /**
     * 验证响应状态码和结构化字段。
     */
    @Tool(description = "执行状态码、字段存在性、字段类型、字段相等和字段包含断言")
    public List<AssertionResult> validateResponse(
            Integer statusCode,
            JsonNode responseBody,
            List<ResponseAssertionRequest> assertions
    ) {
        return responseProcessor.assertResponse(
                statusCode == null ? 0 : statusCode,
                responseBody,
                assertions
        );
    }

    /**
     * 根据持久化工具审计生成结构化报告。
     */
    @Tool(description = "汇总 Agent 任务、工具调用和检索证据，生成结构化测试报告")
    public AgentTestReport generateTestReport(Long projectId, Long taskId) {
        return reportService.generate(projectId, taskId);
    }
}
