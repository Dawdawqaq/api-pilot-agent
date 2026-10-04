# ApiPilot 接口契约

本文件依据 Controller、DTO、VO 和领域类型说明请求方式、路径、参数、响应和异常。代码与运行响应是事实来源；导入的 OpenAPI 描述被测服务，不是 ApiPilot 自身接口。

普通接口返回 `{code,message,data,requestId,timestamp}`，业务成功为 code=SUCCESS，HTTP 200 不保证业务成功。Long 标识和游标以字符串保存，不能转为 JavaScript Number。上传使用 multipart 字段 file；XML 导出为 application/xml；任务流为 text/event-stream，需要保留事件游标和清理旧连接。

新建任务会异步启动，没有独立的假执行入口。确认操作绑定当前 planHash；取消不能撤回已发出的请求；写入结果不确定先核验。草稿变量只保留当前页面会话，不持久化到浏览器。接口新增或字段修改时同步更新本文件，业务含义和错误边界以 Service 为准。

## 1. 完整 Controller 接口清单

以下逐项摘录当前 Controller 路由与方法签名，保留请求体类型、路径变量、查询参数默认值、上传字段、返回类型及显式成功状态。未显式设置的状态按框架正常返回处理；业务异常状态见错误码节。Java 的 Mono/Flux 是服务端容器，不是 JSON 字段。DTO/响应类型在字段定义节展开。

当前共有 64 个 Controller 方法，其中完整声明与字段如下，增量历史和维护接口见后文。知识库和对象存储相关功能要求完整运行模式，质量记录不等于任意模型质量评估。

### AgentTaskController

源码：`src/main/java/com/dochelper/agent/api/AgentTaskController.java`。

#### `POST /api/v1/projects/{projectId}/agent-tasks`

成功状态声明：`@ResponseStatus(HttpStatus.ACCEPTED)`。

```java
public Mono<ApiResponse<AgentTaskResponse>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateAgentTaskRequest request
    )
```

#### `GET /api/v1/projects/{projectId}/agent-tasks`

```java
public Mono<ApiResponse<List<AgentTaskResponse>>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "20") int limit
    )
```

#### `GET /api/v1/projects/{projectId}/agent-tasks/{taskId}`

```java
public Mono<ApiResponse<AgentTaskResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long taskId
    )
```

#### `GET /api/v1/projects/{projectId}/agent-tasks/{taskId}/events`

```java
public Mono<ApiResponse<List<AgentTaskEventResponse>>> events(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "100") int limit
    )
```

#### `GET /api/v1/projects/{projectId}/agent-tasks/{taskId}/stream`

媒体类型声明：`value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE`。

```java
public Flux<ServerSentEvent<AgentTaskEventResponse>> stream(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") long after,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    )
```

#### `POST /api/v1/projects/{projectId}/agent-tasks/{taskId}/modify`

```java
public Mono<ApiResponse<AgentTaskResponse>> modify(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody com.dochelper.agent.api.dto.ModifyPlanRequest request
    )
```

#### `POST /api/v1/projects/{projectId}/agent-tasks/{taskId}/confirmation`

```java
public Mono<ApiResponse<AgentTaskResponse>> confirm(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody ConfirmationDecisionRequest request
    )
```

#### `POST /api/v1/projects/{projectId}/agent-tasks/{taskId}/cancellation`

成功状态声明：`@ResponseStatus(HttpStatus.ACCEPTED)`。

```java
public Mono<ApiResponse<AgentTaskResponse>> cancel(
            @PathVariable Long projectId,
            @PathVariable Long taskId
    )
```

### CompatibilityStreamController

源码：`src/main/java/com/dochelper/compatibility/CompatibilityStreamController.java`。

这是 SSE 兼容性验证接口，发送固定的模拟生命周期事件。仅用于兼容性检查，禁止作为工作台真实任务轨迹或执行结果的数据源。

#### `GET /api/compatibility/stream`

媒体类型声明：`value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE`。

```java
public Flux<ServerSentEvent<CompatibilityEvent>> stream()
```

### ContractTestController

源码：`src/main/java/com/dochelper/contract/api/ContractTestController.java`。

#### `GET /api/v1/projects/{projectId}/contract-tests/negative-cases`

```java
public Mono<ApiResponse<List<NegativeTestCaseResponse>>> negativeCases(
            @PathVariable Long projectId,
            @RequestParam Long endpointId
    )
```

#### `GET /api/v1/projects/{projectId}/contract-tests/replays/{replayId}`

```java
public Mono<ApiResponse<FailureReplayResponse>> getReplay(
            @PathVariable Long projectId,
            @PathVariable Long replayId
    )
```

#### `POST /api/v1/projects/{projectId}/contract-tests/replays/{replayId}`

```java
public Mono<ApiResponse<ScenarioExecutionResponse>> replay(
            @PathVariable Long projectId,
            @PathVariable Long replayId,
            @Valid @RequestBody ReplayFailureRequest request
    )
```

### QualityEvaluationController

源码：`src/main/java/com/dochelper/evaluation/api/QualityEvaluationController.java`。

#### `POST /api/v1/quality-evaluations`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<QualityEvaluationRun>> record(
            @Valid @RequestBody RecordQualityEvaluationRequest request
    )
```

#### `GET /api/v1/quality-evaluations`

```java
public Mono<ApiResponse<List<QualityEvaluationRun>>> list(
            @RequestParam(defaultValue = "10") int limit
    )
```

### ExecutionController

源码：`src/main/java/com/dochelper/executor/api/ExecutionController.java`。

#### `POST /api/v1/projects/{projectId}/executions`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<ScenarioExecutionResponse>> execute(
            @PathVariable Long projectId,
            @Valid @RequestBody ExecuteScenarioRequest request
    )
```

#### `GET /api/v1/projects/{projectId}/executions/{executionId}`

```java
public Mono<ApiResponse<ScenarioExecutionResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long executionId
    )
```

### ProjectModelPolicyController

源码：`src/main/java/com/dochelper/governance/api/ProjectModelPolicyController.java`。

#### `GET /api/v1/projects/{projectId}/model-policy`

```java
public Mono<ApiResponse<ProjectModelPolicy>> get(@PathVariable Long projectId)
```

#### `PUT /api/v1/projects/{projectId}/model-policy`

```java
public Mono<ApiResponse<ProjectModelPolicy>> update(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateModelPolicyRequest request
    )
```

### KnowledgeDocumentController

源码：`src/main/java/com/dochelper/knowledge/api/KnowledgeDocumentController.java`。

#### `POST /api/v1/projects/{projectId}/documents`

媒体类型声明：`consumes = MediaType.MULTIPART_FORM_DATA_VALUE`。

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<KnowledgeDocumentResponse>> upload(
            @PathVariable Long projectId,
            @RequestPart("file") FilePart file
    )
```

#### `GET /api/v1/projects/{projectId}/documents`

```java
public Mono<ApiResponse<List<KnowledgeDocumentResponse>>> list(@PathVariable Long projectId)
```

#### `GET /api/v1/projects/{projectId}/documents/{documentId}`

```java
public Mono<ApiResponse<KnowledgeDocumentResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    )
```

#### `POST /api/v1/projects/{projectId}/documents/{documentId}/retry`

```java
public Mono<ApiResponse<KnowledgeDocumentResponse>> retry(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    )
```

#### `DELETE /api/v1/projects/{projectId}/documents/{documentId}`

```java
public Mono<ApiResponse<Void>> delete(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    )
```

### LlmConfigurationController

源码：`src/main/java/com/dochelper/model/api/LlmConfigurationController.java`。

#### `GET /api/v1/system/llm`

```java
public Mono<ApiResponse<LlmConfigurationResponse>> get()
```

#### `PUT /api/v1/system/llm`

```java
public Mono<ApiResponse<LlmConfigurationResponse>> save(@Valid @RequestBody UpdateLlmConfigurationRequest request)
```

#### `POST /api/v1/system/llm/test`

```java
public Mono<ApiResponse<LlmConnectionTestResponse>> test(@Valid @RequestBody UpdateLlmConfigurationRequest request)
```

### OpenApiController

源码：`src/main/java/com/dochelper/openapi/api/OpenApiController.java`。

#### `POST /api/v1/projects/{projectId}/openapi/imports`

媒体类型声明：`value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE`。

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<OpenApiImportResponse>> importDocument(
            @PathVariable Long projectId,
            @RequestPart("file") FilePart file
    )
```

#### `GET /api/v1/projects/{projectId}/openapi/imports`

```java
public Mono<ApiResponse<List<OpenApiImportResponse>>> listImports(@PathVariable Long projectId)
```

#### `GET /api/v1/projects/{projectId}/openapi/imports/{importId}`

```java
public Mono<ApiResponse<OpenApiImportResponse>> getImport(
            @PathVariable Long projectId,
            @PathVariable Long importId
    )
```

#### `POST /api/v1/projects/{projectId}/openapi/imports/{importId}/retry`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<OpenApiImportResponse>> retryImport(
            @PathVariable Long projectId,
            @PathVariable Long importId
    )
```

#### `GET /api/v1/projects/{projectId}/openapi/endpoints`

```java
public Mono<ApiResponse<List<ApiEndpointResponse>>> listEndpoints(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long importId
    )
```

#### `GET /api/v1/projects/{projectId}/openapi/endpoints/{endpointId}`

```java
public Mono<ApiResponse<ApiEndpointResponse>> getEndpoint(
            @PathVariable Long projectId,
            @PathVariable Long endpointId
    )
```

#### `GET /api/v1/projects/{projectId}/openapi/dependencies`

```java
public Mono<ApiResponse<List<EndpointDependencyEdge>>> dependencies(
            @PathVariable Long projectId
    )
```

### ProjectController

源码：`src/main/java/com/dochelper/project/api/ProjectController.java`。

#### `POST /api/v1/projects`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<ProjectResponse>> createProject(@Valid @RequestBody CreateProjectRequest request)
```

#### `GET /api/v1/projects`

```java
public Mono<ApiResponse<List<ProjectResponse>>> listProjects()
```

#### `GET /api/v1/projects/{projectId}`

```java
public Mono<ApiResponse<ProjectResponse>> getProject(@PathVariable Long projectId)
```

#### `PUT /api/v1/projects/{projectId}`

```java
public Mono<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    )
```

#### `DELETE /api/v1/projects/{projectId}`

```java
public Mono<ApiResponse<Void>> deleteProject(@PathVariable Long projectId)
```

#### `POST /api/v1/projects/{projectId}/environments`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<EnvironmentResponse>> createEnvironment(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEnvironmentRequest request
    )
```

#### `GET /api/v1/projects/{projectId}/environments`

```java
public Mono<ApiResponse<List<EnvironmentResponse>>> listEnvironments(@PathVariable Long projectId)
```

#### `PUT /api/v1/projects/{projectId}/environments/{environmentId}`

```java
public Mono<ApiResponse<EnvironmentResponse>> updateEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long environmentId,
            @Valid @RequestBody UpdateEnvironmentRequest request
    )
```

#### `DELETE /api/v1/projects/{projectId}/environments/{environmentId}`

```java
public Mono<ApiResponse<Void>> deleteEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long environmentId
    )
```

### TestReportController

源码：`src/main/java/com/dochelper/report/api/TestReportController.java`。

#### `GET /api/v1/projects/{projectId}/reports`

```java
public Mono<ApiResponse<List<TestReportResponse>>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "20") int limit
    )
```

#### `GET /api/v1/projects/{projectId}/reports/{reportId}`

```java
public Mono<ApiResponse<TestReportResponse>> get(
            @PathVariable Long projectId,
            @PathVariable Long reportId
    )
```

#### `GET /api/v1/projects/{projectId}/reports/{reportId}/junit.xml`

媒体类型声明：`value = "/{reportId}/junit.xml", produces = MediaType.APPLICATION_XML_VALUE`。

```java
public Mono<ResponseEntity<String>> exportJUnit(
            @PathVariable Long projectId,
            @PathVariable Long reportId
    )
```

### RetrievalController

源码：`src/main/java/com/dochelper/retrieval/api/RetrievalController.java`。

#### `POST /api/v1/projects/{projectId}/retrieval/search`

```java
public Mono<ApiResponse<List<RetrievalResult>>> search(
            @PathVariable Long projectId,
            @Valid @RequestBody HybridSearchRequest request
    )
```

#### `POST /api/v1/projects/{projectId}/retrieval/evaluation-cases`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<EvaluationCaseResponse>> createCase(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEvaluationCaseRequest request
    )
```

#### `GET /api/v1/projects/{projectId}/retrieval/evaluation-cases`

```java
public Mono<ApiResponse<List<EvaluationCaseResponse>>> listCases(@PathVariable Long projectId)
```

#### `POST /api/v1/projects/{projectId}/retrieval/evaluations`

成功状态声明：`@ResponseStatus(HttpStatus.CREATED)`。

```java
public Mono<ApiResponse<EvaluationRunResponse>> runEvaluation(
            @PathVariable Long projectId,
            @Valid @RequestBody RunEvaluationRequest request
    )
```

#### `GET /api/v1/projects/{projectId}/retrieval/evaluations`

```java
public Mono<ApiResponse<List<EvaluationRunResponse>>> listEvaluations(
            @PathVariable Long projectId
    )
```

### SystemController

源码：`src/main/java/com/dochelper/system/api/SystemController.java`。

#### `GET /api/v1/system/overview`

```java
public Mono<ApiResponse<InfrastructureOverviewResponse>> getOverview()
```

### 配置提供的健康接口

`GET /actuator/health`：当前 api.js 使用，来自 Actuator，不由上述 Controller 声明；实际可访问性与详细字段以运行配置和真实响应为准。

## 2. 请求、响应及嵌套类型字段

以下按当前 Java 声明摘录字段与校验注解，不包含转换方法或业务实现。Java 类型需按第 3 节的 JSON 序列化规则理解；`Map` / `Object` / `JsonNode` 为动态 JSON，不代表可以凭空追加约定字段。裸字段声明不能推导绝对非空，条件限制须结合第 2—4 节与真实响应。

### AgentConfirmationResponse

源码：`src/main/java/com/dochelper/agent/api/vo/AgentConfirmationResponse.java`。

```java
public record AgentConfirmationResponse(
        Long id,
        int stepIndex,
        String status,
        String planHash,
        String decisionNote,
        Long decidedByUserId,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {}
```

### AgentEventType

源码：`src/main/java/com/dochelper/agent/domain/AgentEventType.java`。

```java
public enum AgentEventType {
    TASK_CREATED,
    STATE_CHANGED,
    RETRIEVAL_COMPLETED,
    DOCUMENT_EVIDENCE_RETRIEVED,
    OPENAPI_CANDIDATES_SELECTED,
    SCHEMA_CONTEXT_READY,
    PLAN_CREATED,
    TOOL_STARTED,
    TOOL_RETRIED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    CONFIRMATION_REQUIRED,
    CONFIRMATION_DECIDED,
    REPLAN_STARTED,
    PLAN_REVISED,
    CANCEL_REQUESTED,
    TASK_CANCELLED,
    MANUAL_REVIEW_REQUIRED,
    REPORT_GENERATED,
    TASK_COMPLETED;
}
```

### AgentModelCallResponse

源码：`src/main/java/com/dochelper/agent/api/vo/AgentModelCallResponse.java`。

```java
public record AgentModelCallResponse(
        Long id,
        String modelName,
        String status,
        int attempt,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
```

### AgentPlanStep

源码：`src/main/java/com/dochelper/agent/domain/AgentPlanStep.java`。

```java
public record AgentPlanStep(
        int index,
        String objective,
        ExecutionStepRequest request
) {}
```

### AgentTaskEventResponse

源码：`src/main/java/com/dochelper/agent/api/vo/AgentTaskEventResponse.java`。

```java
public record AgentTaskEventResponse(
        long sequenceNo,
        String eventType,
        String state,
        JsonNode payload,
        LocalDateTime createdAt
) {}
```

### AgentTaskResponse

源码：`src/main/java/com/dochelper/agent/api/vo/AgentTaskResponse.java`。

```java
public record AgentTaskResponse(
        Long id,
        Long projectId,
        Long environmentId,
        Long conversationId,
        String goal,
        String status,
        int currentStep,
        int maxSteps,
        int toolCallCount,
        int replanCount,
        int modificationCount,
        List<AgentPlanStep> plan,
        String resultSummary,
        String errorCode,
        String errorMessage,
        boolean cancelRequested,
        AgentConfirmationResponse confirmation,
        List<AgentToolCallResponse> toolCalls,
        List<AgentModelCallResponse> modelCalls,
        LocalDateTime deadlineAt,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {}
```

### AgentTaskStatus

源码：`src/main/java/com/dochelper/agent/domain/AgentTaskStatus.java`。

```java
public enum AgentTaskStatus {
    RECEIVED,
    RETRIEVING,
    PLANNING,
    WAITING_CONFIRMATION,
    EXECUTING,
    OBSERVING,
    REPLANNING,
    REPORTING,
    SUCCEEDED,
    NEEDS_REVIEW,
    FAILED,
    CANCELLED;
}
```

### AgentToolCallResponse

源码：`src/main/java/com/dochelper/agent/api/vo/AgentToolCallResponse.java`。

```java
public record AgentToolCallResponse(
        Long id,
        int stepIndex,
        String toolName,
        String status,
        int attempt,
        JsonNode request,
        JsonNode response,
        Long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
```

### ApiEndpointResponse

源码：`src/main/java/com/dochelper/openapi/api/vo/ApiEndpointResponse.java`。

```java
public record ApiEndpointResponse(
        Long id,
        Long importId,
        String path,
        String httpMethod,
        String operationId,
        String summary,
        String description,
        JsonNode tags,
        boolean deprecated,
        JsonNode requestBody,
        JsonNode responses,
        JsonNode security,
        List<ParameterResponse> parameters
) {}
```

### ApiKeyLocation

源码：`src/main/java/com/dochelper/executor/domain/ApiKeyLocation.java`。

```java
public enum ApiKeyLocation {
    HEADER,
    QUERY;
}
```

### ApiResponse

源码：`src/main/java/com/dochelper/common/api/ApiResponse.java`。

```java
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp
) {}
```

### AssertionResult

源码：`src/main/java/com/dochelper/executor/domain/AssertionResult.java`。

```java
public record AssertionResult(
        AssertionType type,
        String jsonPath,
        boolean passed,
        String expected,
        String actual,
        String message
) {}
```

### AssertionType

源码：`src/main/java/com/dochelper/executor/domain/AssertionType.java`。

```java
public enum AssertionType {
    STATUS_CODE,
    FIELD_EXISTS,
    FIELD_NOT_EXISTS,
    FIELD_TYPE,
    FIELD_EQUALS,
    FIELD_CONTAINS;
}
```

### AuthenticationRequest

源码：`src/main/java/com/dochelper/executor/api/dto/AuthenticationRequest.java`。

```java
public record AuthenticationRequest(
        AuthenticationType type,
        @Size(max = 4096) String token,
        @Size(max = 256) String username,
        @Size(max = 4096) String password,
        @Size(max = 128) String apiKeyName,
        @Size(max = 4096) String apiKeyValue,
        ApiKeyLocation apiKeyLocation
) {}
```

### AuthenticationType

源码：`src/main/java/com/dochelper/executor/domain/AuthenticationType.java`。

```java
public enum AuthenticationType {
    NONE,
    BEARER,
    BASIC,
    API_KEY;
}
```

### CompatibilityEvent

源码：`src/main/java/com/dochelper/compatibility/CompatibilityEvent.java`。

```java
public record CompatibilityEvent(
        int sequence,
        String type,
        String content,
        Instant occurredAt
) {}
```

### ConfirmationDecisionRequest

源码：`src/main/java/com/dochelper/agent/api/dto/ConfirmationDecisionRequest.java`。

```java
public record ConfirmationDecisionRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String note,
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9]{64}") String planHash
) {}
```

### ConfirmationStatus

源码：`src/main/java/com/dochelper/agent/domain/ConfirmationStatus.java`。

```java
public enum ConfirmationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED;
}
```

### CreateAgentTaskRequest

源码：`src/main/java/com/dochelper/agent/api/dto/CreateAgentTaskRequest.java`。

```java
public record CreateAgentTaskRequest(
        Long environmentId,
        Long conversationId,
        @NotBlank @Size(max = 2000) String goal,
        Map<String, Object> initialVariables,
        @Size(max = 20) @Valid List<ExecutionStepRequest> planHint
) {}
```

### CreateEnvironmentRequest

源码：`src/main/java/com/dochelper/project/api/dto/CreateEnvironmentRequest.java`。

```java
public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 512) String baseUrl,
        @Size(max = 128) String allowedMethods,
        Boolean allowPrivateNetwork,
        boolean defaultEnvironment
) {}
```

### CreateEvaluationCaseRequest

源码：`src/main/java/com/dochelper/retrieval/api/dto/CreateEvaluationCaseRequest.java`。

```java
public record CreateEvaluationCaseRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 1000) String query,
        @NotNull Long expectedDocumentId
) {}
```

### CreateProjectRequest

源码：`src/main/java/com/dochelper/project/api/dto/CreateProjectRequest.java`。

```java
public record CreateProjectRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "项目编码必须以小写字母开头，且只能包含小写字母、数字和短横线")
        String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description
) {}
```

### DocumentStatus

源码：`src/main/java/com/dochelper/knowledge/domain/DocumentStatus.java`。

```java
public enum DocumentStatus {
    PROCESSING,
    INDEXED,
    FAILED;
}
```

### EndpointDependencyEdge

源码：`src/main/java/com/dochelper/openapi/domain/EndpointDependencyEdge.java`。

```java
public record EndpointDependencyEdge(
        Long producerEndpointId,
        String producerOperationId,
        Long consumerEndpointId,
        String consumerOperationId,
        String sharedField,
        double confidence,
        String reason
) {}
```

### EnvironmentResponse

源码：`src/main/java/com/dochelper/project/api/vo/EnvironmentResponse.java`。

```java
public record EnvironmentResponse(
        Long id,
        Long projectId,
        String name,
        String baseUrl,
        String allowedMethods,
        boolean allowPrivateNetwork,
        boolean defaultEnvironment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
```

### EvaluationCaseResponse

源码：`src/main/java/com/dochelper/retrieval/api/vo/EvaluationCaseResponse.java`。

```java
public record EvaluationCaseResponse(
        Long id,
        String name,
        String query,
        Long expectedDocumentId,
        LocalDateTime createdAt
) {}
```

### EvaluationRunResponse

源码：`src/main/java/com/dochelper/retrieval/api/vo/EvaluationRunResponse.java`。

```java
public record EvaluationRunResponse(
        Long id,
        int topK,
        int caseCount,
        int hitCount,
        double recallAtK,
        JsonNode details,
        LocalDateTime createdAt
) {}
```

### ExecuteScenarioRequest

源码：`src/main/java/com/dochelper/executor/api/dto/ExecuteScenarioRequest.java`。

```java
public record ExecuteScenarioRequest(
        Long environmentId,
        Map<String, Object> initialVariables,
        @NotNull @NotEmpty @Size(max = 50) @Valid List<ExecutionStepRequest> steps
) {}
```

### ExecutionStatus

源码：`src/main/java/com/dochelper/executor/domain/ExecutionStatus.java`。

```java
public enum ExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED;
}
```

### ExecutionStepRequest

源码：`src/main/java/com/dochelper/executor/api/dto/ExecutionStepRequest.java`。

```java
public record ExecutionStepRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 10) String method,
        @NotBlank @Size(max = 1000) String path,
        Map<String, String> pathVariables,
        Map<String, String> queryParams,
        Map<String, String> headers,
        JsonNode body,
        @Valid AuthenticationRequest authentication,
        @Valid List<VariableExtractorRequest> extractors,
        @Valid List<ResponseAssertionRequest> assertions,
        Boolean dangerousOperationConfirmed
) {}
```

### ExecutionStepResponse

源码：`src/main/java/com/dochelper/executor/api/vo/ExecutionStepResponse.java`。

```java
public record ExecutionStepResponse(
        int stepIndex,
        String name,
        String method,
        String requestUrl,
        int responseStatus,
        JsonNode responseBody,
        Map<String, Object> extractedVariables,
        List<AssertionResult> assertions,
        boolean success,
        long durationMs,
        String errorMessage
) {}
```

### FailureReplayResponse

源码：`src/main/java/com/dochelper/contract/api/vo/FailureReplayResponse.java`。

```java
public record FailureReplayResponse(
        Long id,
        Long executionId,
        int stepIndex,
        String requestFingerprint,
        JsonNode request,
        String errorSummary,
        LocalDateTime createdAt
) {}
```

### HybridSearchRequest

源码：`src/main/java/com/dochelper/retrieval/api/dto/HybridSearchRequest.java`。

```java
public record HybridSearchRequest(
        @NotBlank @Size(max = 1000) String query,
        @Min(1) @Max(20) Integer topK
) {}
```

### ImportStatus

源码：`src/main/java/com/dochelper/openapi/domain/ImportStatus.java`。

```java
public enum ImportStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED;
}
```

### InfrastructureOverviewResponse

源码：`src/main/java/com/dochelper/system/api/vo/InfrastructureOverviewResponse.java`。

```java
public record InfrastructureOverviewResponse(
        String application,
        String schemaVersion,
        String qdrantCollection,
        String objectStorageBucket,
        boolean knowledgeEnabled,
        String chatModel,
        String modelMode,
        String modelProvider
) {}
```

### KnowledgeDocumentResponse

源码：`src/main/java/com/dochelper/knowledge/api/vo/KnowledgeDocumentResponse.java`。

```java
public record KnowledgeDocumentResponse(
        Long id,
        Long projectId,
        String fileName,
        String contentType,
        long fileSize,
        String contentHash,
        String title,
        DocumentStatus status,
        String errorMessage,
        int chunkCount,
        LocalDateTime createdAt,
        LocalDateTime indexedAt,
        LocalDateTime updatedAt
) {}
```

### LlmConfigurationResponse

源码：`src/main/java/com/dochelper/model/api/vo/LlmConfigurationResponse.java`。

```java
public record LlmConfigurationResponse(String mode, String provider, String baseUrl, String model,
        String activeModel, boolean apiKeyConfigured, String source, boolean persistentStorageReady) {}
```

### LlmConnectionTestResponse

源码：`src/main/java/com/dochelper/model/api/vo/LlmConnectionTestResponse.java`。

```java
public record LlmConnectionTestResponse(boolean success, String message, String model, long durationMs) {}
```

### ModifyPlanRequest

源码：`src/main/java/com/dochelper/agent/api/dto/ModifyPlanRequest.java`。

```java
public record ModifyPlanRequest(
        @NotBlank(message = "修改指令不能为空")
        @Size(max = 2000, message = "修改指令不能超过 2000 个字符")
        String instruction
) {}
```

### NegativeTestCaseResponse

源码：`src/main/java/com/dochelper/contract/api/vo/NegativeTestCaseResponse.java`。

```java
public record NegativeTestCaseResponse(
        String caseType,
        String target,
        String mutation,
        String expectedOutcome
) {}
```

### OpenApiImportResponse

源码：`src/main/java/com/dochelper/openapi/api/vo/OpenApiImportResponse.java`。

```java
public record OpenApiImportResponse(
        Long id,
        Long projectId,
        int revisionNumber,
        Long retryOfId,
        String fileName,
        String contentHash,
        String specificationVersion,
        String documentTitle,
        String documentVersion,
        ImportStatus status,
        String errorMessage,
        int endpointCount,
        int schemaCount,
        int securitySchemeCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
```

### ParameterResponse

源码：`src/main/java/com/dochelper/openapi/api/vo/ParameterResponse.java`。

```java
public record ParameterResponse(
        String name,
        String location,
        boolean required,
        String description,
        JsonNode schema
) {}
```

### ProjectModelPolicy

源码：`src/main/java/com/dochelper/governance/domain/ProjectModelPolicy.java`。

```java
public record ProjectModelPolicy(
        Long projectId,
        boolean externalModelAllowed,
        String allowedProvider,
        boolean allowDocumentContent,
        boolean allowSchemaContent,
        int promptCharacterBudget,
        int endpointTopK
) {}
```

### ProjectResponse

源码：`src/main/java/com/dochelper/project/api/vo/ProjectResponse.java`。

```java
public record ProjectResponse(
        Long id,
        String code,
        String name,
        String description,
        ProjectStatus status,
        Long currentImportId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
```

### ProjectStatus

源码：`src/main/java/com/dochelper/project/domain/ProjectStatus.java`。

```java
public enum ProjectStatus {
    ACTIVE,
    ARCHIVED;
}
```

### QualityEvaluationRun

源码：`src/main/java/com/dochelper/evaluation/domain/QualityEvaluationRun.java`。

```java
public record QualityEvaluationRun(
        Long id,
        String datasetVersion,
        int serviceCount,
        int evaluationCaseCount,
        int securityCaseCount,
        int passedCaseCount,
        int blockedAttackCount,
        double taskSuccessRate,
        double validPlanRate,
        double securityBlockRate,
        long p95TaskDurationMs,
        long totalModelTokens,
        String metricsJson,
        LocalDateTime createdAt
) {}
```

### RecordQualityEvaluationRequest

源码：`src/main/java/com/dochelper/evaluation/api/dto/RecordQualityEvaluationRequest.java`。

```java
public record RecordQualityEvaluationRequest(
        @NotBlank @Size(max = 64) String datasetVersion,
        @Min(1) int serviceCount,
        @Min(1) int evaluationCaseCount,
        @Min(1) int securityCaseCount,
        @Min(0) int passedCaseCount,
        @Min(0) int blockedAttackCount,
        @DecimalMin("0") @DecimalMax("1") double taskSuccessRate,
        @DecimalMin("0") @DecimalMax("1") double validPlanRate,
        @DecimalMin("0") @DecimalMax("1") double securityBlockRate,
        @Min(0) long p95TaskDurationMs,
        @Min(0) long totalModelTokens,
        @NotNull Map<String, Object> metrics
) {}
```

### ReplayFailureRequest

源码：`src/main/java/com/dochelper/contract/api/dto/ReplayFailureRequest.java`。

```java
public record ReplayFailureRequest(@NotNull Long environmentId) {}
```

### ResponseAssertionRequest

源码：`src/main/java/com/dochelper/executor/api/dto/ResponseAssertionRequest.java`。

```java
public record ResponseAssertionRequest(
        @NotNull AssertionType type,
        @Size(max = 500) String jsonPath,
        JsonNode expectedValue,
        @Size(max = 32) String expectedType
) {}
```

### RetrievalResult

源码：`src/main/java/com/dochelper/retrieval/domain/RetrievalResult.java`。

```java
public record RetrievalResult(
        Long chunkId,
        Long documentId,
        String sourceName,
        String section,
        int chunkIndex,
        String content,
        double fusedScore,
        Integer keywordRank,
        Integer vectorRank,
        String citation
) {}
```

### RunEvaluationRequest

源码：`src/main/java/com/dochelper/retrieval/api/dto/RunEvaluationRequest.java`。

```java
public record RunEvaluationRequest(@Min(1) @Max(20) Integer topK) {}
```

### ScenarioExecutionResponse

源码：`src/main/java/com/dochelper/executor/api/vo/ScenarioExecutionResponse.java`。

```java
public record ScenarioExecutionResponse(
        Long id,
        Long projectId,
        Long environmentId,
        ExecutionStatus status,
        int stepCount,
        int completedStepCount,
        long durationMs,
        String errorCode,
        String errorMessage,
        List<ExecutionStepResponse> steps,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
```

### StepExecutionStatus

源码：`src/main/java/com/dochelper/executor/domain/StepExecutionStatus.java`。

```java
public enum StepExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED;
}
```

### TestReportResponse

源码：`src/main/java/com/dochelper/report/api/vo/TestReportResponse.java`。

```java
public record TestReportResponse(
        Long id,
        Long projectId,
        Long taskId,
        Long executionId,
        String title,
        String status,
        String summary,
        int totalSteps,
        int passedSteps,
        int failedSteps,
        int totalToolCalls,
        long durationMs,
        List<String> evidenceCitations,
        JsonNode metrics,
        List<TestReportStepResponse> steps,
        LocalDateTime createdAt
) {}
```

### TestReportStepResponse

源码：`src/main/java/com/dochelper/report/api/vo/TestReportStepResponse.java`。

```java
public record TestReportStepResponse(
        Long id,
        int stepIndex,
        String stepName,
        String httpMethod,
        String requestUrl,
        JsonNode requestHeaders,
        JsonNode requestBody,
        Integer responseStatus,
        JsonNode responseHeaders,
        JsonNode responseBody,
        JsonNode assertions,
        boolean success,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {}
```

### ToolCallStatus

源码：`src/main/java/com/dochelper/agent/domain/ToolCallStatus.java`。

```java
public enum ToolCallStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    REJECTED;
}
```

### UpdateEnvironmentRequest

源码：`src/main/java/com/dochelper/project/api/dto/UpdateEnvironmentRequest.java`。

```java
public record UpdateEnvironmentRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 512) String baseUrl,
        @Size(max = 128) String allowedMethods,
        Boolean allowPrivateNetwork,
        boolean defaultEnvironment
) {}
```

### UpdateLlmConfigurationRequest

源码：`src/main/java/com/dochelper/model/api/dto/UpdateLlmConfigurationRequest.java`。

```java
public record UpdateLlmConfigurationRequest(
        @NotBlank @Pattern(regexp = "API|OFFLINE", message = "请选择 API 或 OFFLINE") String mode,
        @NotBlank @Pattern(regexp = "DEEPSEEK|DASHSCOPE|OPENAI_COMPATIBLE", message = "模型供应商无效") String provider,
        @Size(max = 512) String baseUrl,
        @Size(max = 128) String model,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 4096) String apiKey
) {}
```

### UpdateModelPolicyRequest

源码：`src/main/java/com/dochelper/governance/api/dto/UpdateModelPolicyRequest.java`。

```java
public record UpdateModelPolicyRequest(
        boolean externalModelAllowed,
        @NotBlank @Size(max = 64) String allowedProvider,
        boolean allowDocumentContent,
        boolean allowSchemaContent,
        @Min(2000) @Max(200000) int promptCharacterBudget,
        @Min(1) @Max(50) int endpointTopK
) {}
```

### UpdateProjectRequest

源码：`src/main/java/com/dochelper/project/api/dto/UpdateProjectRequest.java`。

```java
public record UpdateProjectRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description,
        @NotNull ProjectStatus status
) {}
```

### VariableExtractorRequest

源码：`src/main/java/com/dochelper/executor/api/dto/VariableExtractorRequest.java`。

```java
public record VariableExtractorRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$")
        String name,
        @NotBlank @Size(max = 500) String jsonPath
) {}
```

## 3. 当前业务错误码

以下摘录错误枚举中的编号、提示和 HTTP 状态。具体在哪种业务条件触发需查对应 Service；不要把全部错误当作同一种失败。连接测试也可能正常返回 `success: false`，因此不能只检查 HTTP 状态。

| 模块枚举 | 常量 | code | 默认提示 | HTTP 状态 |
| --- | --- | --- | --- | --- |
| AgentErrorCode | TASK_CANCELLED | AGENT_CANCELLED | 用户取消任务，已停止后续步骤；已发出的请求不能撤回 | CONFLICT |
| AgentErrorCode | RECOVERY_UNAVAILABLE | AGENT_409_005 | 任务上下文无法安全恢复，请核验已执行结果后新建任务 | CONFLICT |
| AgentErrorCode | TASK_NOT_FOUND | AGENT_404_001 | Agent 任务不存在 | NOT_FOUND |
| AgentErrorCode | INVALID_TASK | AGENT_400_001 | Agent 任务参数不合法 | BAD_REQUEST |
| AgentErrorCode | INVALID_PLAN | AGENT_400_002 | Agent 执行计划不合法 | BAD_REQUEST |
| AgentErrorCode | INVALID_STATE | AGENT_409_001 | 当前任务状态不允许该操作 | CONFLICT |
| AgentErrorCode | CONFIRMATION_NOT_FOUND | AGENT_404_002 | 待确认操作不存在 | NOT_FOUND |
| AgentErrorCode | CONFIRMATION_EXPIRED | AGENT_409_002 | 危险操作确认已过期 | CONFLICT |
| AgentErrorCode | TOOL_CALL_LIMIT | AGENT_429_001 | Agent 工具调用次数达到上限 | TOO_MANY_REQUESTS |
| AgentErrorCode | PLAN_MODIFICATION_LIMIT | AGENT_429_002 | 任务计划修改轮次已达上限 | TOO_MANY_REQUESTS |
| AgentErrorCode | TASK_CAPACITY_EXCEEDED | AGENT_429_003 | 当前运行任务已达到容量上限，请等待现有任务结束 | TOO_MANY_REQUESTS |
| AgentErrorCode | TASK_TIMEOUT | AGENT_408_001 | Agent 任务执行超时 | REQUEST_TIMEOUT |
| AgentErrorCode | DUPLICATE_TOOL_CALL | AGENT_409_003 | 检测到重复工具调用 | CONFLICT |
| AgentErrorCode | TASK_BUSY | AGENT_409_004 | 任务正在处理或修改中，请稍后重试 | CONFLICT |
| AgentErrorCode | PLANNING_FAILED | AGENT_422_001 | Agent 无法生成可执行计划 | UNPROCESSABLE_ENTITY |
| CommonErrorCode | INVALID_ARGUMENT | COMMON_400_001 | 请求参数不合法 | BAD_REQUEST |
| CommonErrorCode | RESOURCE_NOT_FOUND | COMMON_404_001 | 请求的资源不存在 | NOT_FOUND |
| CommonErrorCode | INTERNAL_ERROR | COMMON_500_001 | 系统内部错误 | INTERNAL_SERVER_ERROR |
| ExecutionErrorCode | ENVIRONMENT_REQUIRED | EXECUTOR_400_001 | 项目没有可用的执行环境 | BAD_REQUEST |
| ExecutionErrorCode | INVALID_STEP | EXECUTOR_400_002 | 执行步骤配置无效 | BAD_REQUEST |
| ExecutionErrorCode | METHOD_NOT_ALLOWED | EXECUTOR_403_001 | 当前环境不允许该 HTTP 方法 | FORBIDDEN |
| ExecutionErrorCode | WRITE_CONFIRMATION_REQUIRED | EXECUTOR_409_002 | 写操作必须经过人工确认 | CONFLICT |
| ExecutionErrorCode | WRITE_RESULT_REQUIRES_REVIEW | EXECUTOR_409_003 | 写操作可能已经生效，需要核验远端结果，禁止自动重放 | CONFLICT |
| ExecutionErrorCode | ENDPOINT_NOT_IN_CATALOG | EXECUTOR_403_003 | 请求接口不在当前 OpenAPI 目录 | FORBIDDEN |
| ExecutionErrorCode | ENDPOINT_METHOD_MISMATCH | EXECUTOR_403_004 | 请求方法与 OpenAPI 目录不一致 | FORBIDDEN |
| ExecutionErrorCode | TARGET_BLOCKED | EXECUTOR_403_002 | 目标地址被 SSRF 安全策略拒绝 | FORBIDDEN |
| ExecutionErrorCode | VARIABLE_NOT_FOUND | EXECUTOR_422_001 | 模板变量不存在 | UNPROCESSABLE_ENTITY |
| ExecutionErrorCode | INVALID_JSON_PATH | EXECUTOR_422_002 | JSONPath 表达式无效或未命中 | UNPROCESSABLE_ENTITY |
| ExecutionErrorCode | REQUEST_TOO_LARGE | EXECUTOR_413_001 | 请求体超过大小限制 | PAYLOAD_TOO_LARGE |
| ExecutionErrorCode | RESPONSE_TOO_LARGE | EXECUTOR_413_002 | 响应体超过大小限制 | PAYLOAD_TOO_LARGE |
| ExecutionErrorCode | REQUEST_TIMEOUT | EXECUTOR_504_001 | 被测接口响应超时 | GATEWAY_TIMEOUT |
| ExecutionErrorCode | REMOTE_REQUEST_FAILED | EXECUTOR_502_001 | 被测接口调用失败 | BAD_GATEWAY |
| ExecutionErrorCode | EXECUTION_NOT_FOUND | EXECUTOR_404_001 | 执行记录不存在 | NOT_FOUND |
| KnowledgeErrorCode | FEATURE_DISABLED | KNOWLEDGE_503_001 | 当前运行模式未启用业务知识库，仍可导入 OpenAPI 并执行 API 测试 | SERVICE_UNAVAILABLE |
| KnowledgeErrorCode | EMPTY_FILE | KNOWLEDGE_400_001 | 文档文件不能为空 | BAD_REQUEST |
| KnowledgeErrorCode | FILE_TOO_LARGE | KNOWLEDGE_413_001 | 文档文件超过大小限制 | PAYLOAD_TOO_LARGE |
| KnowledgeErrorCode | DOCUMENT_NOT_FOUND | KNOWLEDGE_404_001 | 知识库文档不存在 | NOT_FOUND |
| KnowledgeErrorCode | DOCUMENT_NOT_RETRYABLE | KNOWLEDGE_409_001 | 仅失败文档可以重试索引 | CONFLICT |
| KnowledgeErrorCode | DOCUMENT_NOT_INDEXED | KNOWLEDGE_409_002 | 知识库文档尚未完成索引 | CONFLICT |
| KnowledgeErrorCode | EXTRACTION_FAILED | KNOWLEDGE_422_001 | 文档文本解析失败 | UNPROCESSABLE_ENTITY |
| KnowledgeErrorCode | INDEXING_FAILED | KNOWLEDGE_500_001 | 文档索引失败 | INTERNAL_SERVER_ERROR |
| KnowledgeErrorCode | EMPTY_QUERY | RETRIEVAL_400_001 | 检索问题不能为空 | BAD_REQUEST |
| KnowledgeErrorCode | EVALUATION_CASE_CONFLICT | RETRIEVAL_409_001 | 评测用例名称已存在 | CONFLICT |
| KnowledgeErrorCode | NO_EVALUATION_CASES | RETRIEVAL_409_002 | 当前项目没有检索评测用例 | CONFLICT |
| OpenApiErrorCode | EMPTY_FILE | OPENAPI_400_001 | OpenAPI 文件不能为空 | BAD_REQUEST |
| OpenApiErrorCode | FILE_TOO_LARGE | OPENAPI_413_001 | OpenAPI 文件超过大小限制 | PAYLOAD_TOO_LARGE |
| OpenApiErrorCode | UNSUPPORTED_FILE_TYPE | OPENAPI_400_002 | 仅支持 JSON、YAML 和 YML 文件 | BAD_REQUEST |
| OpenApiErrorCode | INVALID_DOCUMENT | OPENAPI_422_001 | OpenAPI 文档解析失败 | UNPROCESSABLE_ENTITY |
| OpenApiErrorCode | IMPORT_NOT_FOUND | OPENAPI_404_001 | OpenAPI 导入记录不存在 | NOT_FOUND |
| OpenApiErrorCode | IMPORT_NOT_RETRYABLE | OPENAPI_409_001 | 仅失败的导入记录可以重试 | CONFLICT |
| OpenApiErrorCode | IMPORT_PROCESSING_FAILED | OPENAPI_500_001 | OpenAPI 导入处理失败 | INTERNAL_SERVER_ERROR |
| ProjectErrorCode | PROJECT_NOT_FOUND | PROJECT_404_001 | 被测项目不存在 | NOT_FOUND |
| ProjectErrorCode | PROJECT_CODE_CONFLICT | PROJECT_409_001 | 项目编码已存在 | CONFLICT |
| ProjectErrorCode | ENVIRONMENT_NOT_FOUND | PROJECT_404_002 | 项目环境不存在 | NOT_FOUND |
| ProjectErrorCode | ENVIRONMENT_NAME_CONFLICT | PROJECT_409_002 | 项目环境名称已存在 | CONFLICT |
| ProjectErrorCode | INVALID_BASE_URL | PROJECT_400_001 | Base URL 必须是合法的 HTTP 或 HTTPS 地址 | BAD_REQUEST |
| ReportErrorCode | REPORT_NOT_FOUND | REPORT_404_001 | 测试报告不存在 | NOT_FOUND |
| ReportErrorCode | EXECUTION_NOT_FOUND | REPORT_422_001 | 任务没有可关联的执行记录 | UNPROCESSABLE_ENTITY |

错误映射源码：`src/main/java/com/dochelper/common/exception/GlobalExceptionHandler.java`。字段校验信息可包含具体字段名；展示服务端可理解的 message，并保留 requestId 便于定位问题。

## 4. 个人体验增量：历史搜索与游标分页（2026-10-04）

新增 GET `/api/v1/projects/{projectId}/agent-tasks/history` 和 GET `/api/v1/projects/{projectId}/reports/history`。两项共用 `limit`（默认 20，1—100）、`beforeId`（可选正整数游标）、`query`（去除首尾空白，最长 200 字符），按 ID 倒序查询字面子串，不将搜索中的 `%` 和 `_` 当通配符。

ApiResponse.data 为 `{items,total,hasMore,nextCursor}`。items 沿用任务/报告响应类型，但只返回摘要，详情按原详情接口读取；total 是项目内符合搜索条件的总数，Long 字段沿用字符串序列化；nextCursor 必须原样传递，不转 Number。搜索词变化时清空旧页；hasMore 为 false 时不继续读取。旧列表接口仍返回数组，未破坏旧版。

参数错误 HTTP 400 / COMMON_400_001；项目不存在 HTTP 404 / PROJECT_404_001；空结果 SUCCESS 且 items=[]、total=0、nextCursor=null。具体响应、异常、草稿与失败指引行为和验收证据见 [运行与维护](personal-operations.md)。

## 5. 剩余个人体验接口（2026-10-04）

以下 11 个实际 Controller 方法沿用 ApiResponse `{code,message,data,requestId,timestamp}`。Long 标识和游标保持字符串，不转 Number；旧接口不移除。部署、嵌入和删除语义见 [个人使用与维护](personal-operations.md)。

| 方法与路径 | 参数 | data 与用途 |
| --- | --- | --- |
| GET `/api/v1/projects/{projectId}/readiness` | 可选 environmentId | `{ready,checks:[{key,label,status,message,action}]}`；状态 READY/BLOCKED/OPTIONAL，action 为 projects/settings/openapi/knowledge |
| GET `/api/v1/system/embedding` | 无 | 当前嵌入配置，不返回 Key |
| POST `/api/v1/system/embedding/test` | 下述嵌入请求 | `{success,message,durationMs,dimensions?}`；连接失败可 HTTP 200 + success=false |
| POST `/api/v1/system/embedding/rebuild` | 下述嵌入请求 | 全部成功后返回新的生效配置；失败保留旧配置 |
| POST `/api/v1/system/embedding/cleanup` | 无请求体 | 清理已登记未启用集合后的生效配置 |
| GET `/api/v1/projects/{projectId}/contract-tests/replays` | limit、beforeId、query、可选 executionId | CursorPage `{items,total,hasMore,nextCursor}`，items 沿用 FailureReplayResponse |
| GET `/api/v1/projects/recycled` | 无 | `[{id,code,name}]`，仅回收站项目 |
| POST `/api/v1/projects/{projectId}/restoration` | 无请求体 | true；仅恢复被逻辑删除的项目 |
| GET `/api/v1/projects/{projectId}/data` | 无 | 下述实际统计 |
| POST `/api/v1/projects/{projectId}/data/cleanup-preview` | retentionDays 默认 30，7—3650 | `{previewId,before,expiresAt,tasks,reports,executions,message}` |
| POST `/api/v1/projects/{projectId}/data/cleanup` | `{previewId,projectCode}` | 实际 `{tasks,reports,executions}` 删除数 |

嵌入请求：`{mode,baseUrl,model,dimensions,apiKey,allowDocumentTransfer}`。mode 为 DEVELOPMENT 或 API；baseUrl 最大 512、model 最大 200、apiKey 最大 4096 字符，dimensions 可空或 1—8192。API 模式须填写合法 http/https 基础地址和模型，禁止带用户信息、query、fragment 或完整 /embeddings 路径。空 Key 只可复用同地址已保存的独立嵌入 Key。重建 API 时 allowDocumentTransfer 必须 true，测试连接不要求发送文档许可；DEVELOPMENT 不调用外部服务。

嵌入配置字段：enabled、mode、baseUrl、model、dimensions（实际返回维度）、requestedDimensions（可空的供应商参数）、collection、apiKeyConfigured、persistentStorageReady、rebuilding、rebuildRequired、unusedCollections、indexedDocuments、indexedChunks、message。编辑表单使用 requestedDimensions，不将实际维度强制回填为供应商参数。重建是应用级，包含回收站项目保留的已索引文档；不能包装成仅重建当前项目。rebuildRequired 为备份恢复后的门禁，必须成功重建才可重新检索/写入。

回放分页共用历史查询节规则；executionId 如提供必须为正整数，只在当前项目筛选。query 按 errorSummary 字面子串搜索，不按 JSON 搜索。列表/详情只展示脱敏字段，回放仍使用原 POST `/replays/{id}` + `{environmentId}`。前端仅允许只读请求直接回放，写操作回到工作台人工确认；负向用例是生成结果，不自动执行。

统计键：agent_task、test_report、api_execution、failure_replay_sample、openapi_import、knowledge_document、knowledge_chunk、documentBytes、activeTasks。documentBytes 是未删除文档文件元数据总和，其他数量包含留存历史，不能展示成物理存储占用。

清理预览 5 分钟有效，绑定项目和范围，新预览覆盖旧预览。按任务 completedAt/报告 createdAt/执行 completedAt 判定保留期，每批最多 500 个任务和 500 次执行；保留进行中、有效租约、NEEDS_REVIEW 及其他保留证据引用的执行。确认输入项目完整 code，成功清空预览，失败重新预览。接口资料、环境、业务文档、当前模型配置不属于历史清理，不提供整项目永久删除按钮。

异常：格式/范围、预览失效、编码错误、范围改变、嵌入重建失败或索引恢复门禁为 HTTP 400 / COMMON_400_001；禁用知识库的重建/清理 HTTP 503 / KNOWLEDGE_503_001；项目不存在/已经恢复为 HTTP 404 / PROJECT_404_001；编码冲突 HTTP 409 / PROJECT_409_001。原项目归档/回收接口存在进行中任务时 HTTP 409 / PROJECT_409_004，归档项目创建任务/执行时 HTTP 409 / PROJECT_409_003。未处理基础设施错误仍为统一 500，保留 requestId。

当前前端由 Maven 和 Docker 构建入 jar；预览模式只使用预览数据，禁用嵌入、清理、恢复、回放写请求并避免读取真实配置。
