package com.dochelper.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.dochelper.DocHelperApplication;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.infrastructure.redis.NamespacedRedisKeyFactory;
import com.dochelper.infrastructure.storage.ObjectStorageGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * 验证主机应用与公共开发设施的完整连接和项目隔离。
 */
@ActiveProfiles({"local", "stub"})
@SpringBootTest(
        classes = DocHelperApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "dochelper.auth.bootstrap-username=integration-owner",
                "dochelper.auth.bootstrap-password=Integration-Test-Password-2026!",
                "dochelper.auth.bootstrap-display-name=集成测试 Owner",
                "dochelper.auth.jwt-secret=integration-jwt-secret",
                "dochelper.secret-store.master-key=integration-secret-store-key"
        }
)
class PublicInfrastructureIT {

    private static final byte[] EXECUTION_OPENAPI = """
            openapi: 3.0.3
            info:
              title: 执行器集成测试接口
              version: 1.0.0
            paths:
              /auth/login:
                post:
                  operationId: login
                  responses:
                    '200':
                      description: 登录成功
              /users/{userId}:
                get:
                  operationId: getUser
                  parameters:
                    - name: userId
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    '200':
                      description: 查询成功
                delete:
                  operationId: deleteUser
                  responses:
                    '204':
                      description: 删除成功
              /blocked:
                get:
                  operationId: blockedPrivateTarget
                  responses:
                    '200':
                      description: 仅用于验证目标地址策略
              /agent/login:
                post:
                  operationId: agentLogin
                  responses:
                    '200':
                      description: 登录成功
              /agent/users/{userId}:
                parameters:
                  - name: userId
                    in: path
                    required: true
                    schema:
                      type: integer
                get:
                  operationId: getAgentUser
                  responses:
                    '200':
                      description: 查询成功
                delete:
                  operationId: deleteAgentUser
                  responses:
                    '204':
                      description: 删除成功
            """.getBytes(StandardCharsets.UTF_8);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NamespacedRedisKeyFactory redisKeyFactory;

    @Autowired
    private ObjectStorageGateway objectStorageGateway;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    /**
     * 验证 MySQL、Redis、MinIO 和系统接口均使用 DocHelper 独立资源。
     *
     * @throws Exception MinIO 访问异常
     */
    @Test
    void shouldUseIsolatedPublicInfrastructure() throws Exception {
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(databaseName).isEqualTo("dochelper");

        String redisKey = redisKeyFactory.create("stage1:integration-probe");
        try {
            redisTemplate.opsForValue().set(redisKey, "ok", Duration.ofSeconds(30));
            assertThat(redisTemplate.opsForValue().get(redisKey)).isEqualTo("ok");
        } finally {
            redisTemplate.delete(redisKey);
        }

        assertThat(objectStorageGateway.bucketName()).isEqualTo("dochelper-files");
        assertThat(objectStorageGateway.bucketExists()).isTrue();

        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/api/v1/system/overview")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data.schemaVersion").isEqualTo("14")
                .jsonPath("$.data.redisNamespace").isEqualTo("dochelper:")
                .jsonPath("$.data.qdrantCollection").isEqualTo("dochelper_knowledge")
                .jsonPath("$.data.objectStorageBucket").isEqualTo("dochelper-files");
    }

    /**
     * 验证 Actuator 能汇总四类公共设施的健康状态。
     */
    @Test
    void shouldExposeInfrastructureHealth() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components.db.status").isEqualTo("UP")
                .jsonPath("$.components.redis.status").isEqualTo("UP")
                .jsonPath("$.components.qdrant.status").isEqualTo("UP")
                .jsonPath("$.components.minio.status").isEqualTo("UP");
    }

    /**
     * 验证匿名免登录模式下项目的创建、查询与全功能访问。
     */
    @Test
    void shouldAllowAnonymousProjectOperations() {
        WebTestClient client = authenticatedClient();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long projectA = createProject(client, "anon-a-" + suffix, "匿名开源项目 A");
        long projectB = createProject(client, "anon-b-" + suffix, "匿名开源项目 B");

        client.get().uri("/api/v1/projects/{projectId}", projectA)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/v1/projects/{projectId}", projectB)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray();
    }

    /**
     * 验证质量评测记录通过仓储持久化，并可按时间倒序查询。
     */
    @Test
    void shouldPersistAndListQualityEvaluationRuns() {
        WebTestClient owner = authenticatedClient();
        String datasetVersion = "integration-" + UUID.randomUUID();
        owner.post()
                .uri("/api/v1/quality-evaluations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "datasetVersion": "%s",
                          "serviceCount": 3,
                          "evaluationCaseCount": 50,
                          "securityCaseCount": 15,
                          "passedCaseCount": 42,
                          "blockedAttackCount": 15,
                          "taskSuccessRate": 0.84,
                          "validPlanRate": 0.90,
                          "securityBlockRate": 1.0,
                          "p95TaskDurationMs": 1250,
                          "totalModelTokens": 6400,
                          "metrics": {"source": "integration-test"}
                        }
                        """.formatted(datasetVersion))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.datasetVersion").isEqualTo(datasetVersion)
                .jsonPath("$.data.securityBlockRate").isEqualTo(1.0);

        owner.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/quality-evaluations")
                        .queryParam("limit", 1)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].datasetVersion").isEqualTo(datasetVersion);
    }

    /**
     * 验证两个实例并发领取同一任务时只有一个租约写入成功，租约释放后可再次领取。
     */
    @Test
    void shouldAllowOnlyOneConcurrentTaskLeaseOwner() throws Exception {
        WebTestClient owner = authenticatedClient();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long projectId = createProject(owner, "lease-" + suffix, "任务租约项目");
        JsonNode environment = owner.post()
                .uri("/api/v1/projects/{projectId}/environments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "lease-environment",
                          "baseUrl": "https://example.com",
                          "defaultEnvironment": true
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(environment).isNotNull();
        long environmentId = environment.path("data").path("id").asLong();
        long conversationId = IdWorker.getId();
        long taskId = IdWorker.getId();
        jdbcTemplate.update(
                """
                        INSERT INTO agent_conversation(id, project_id, title, status)
                        VALUES (?, ?, '租约并发验证', 'ACTIVE')
                        """,
                conversationId,
                projectId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO agent_task(
                            id, project_id, environment_id, conversation_id, goal, status,
                            current_step, max_steps, tool_call_count, replan_count,
                            plan_json, context_json_redacted, cancel_requested, lock_version, deadline_at
                        ) VALUES (?, ?, ?, ?, '验证租约互斥', 'FAILED', 0, 1, 0, 0, '[]', '{}', 0, 0, ?)
                        """,
                taskId,
                projectId,
                environmentId,
                conversationId,
                LocalDateTime.now().plusMinutes(5)
        );

        CountDownLatch startGate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime leaseUntil = now.plusSeconds(30);
            Future<Boolean> first = executor.submit(() -> {
                startGate.await();
                return agentTaskRepository.tryAcquireLease(taskId, "instance-a", now, leaseUntil);
            });
            Future<Boolean> second = executor.submit(() -> {
                startGate.await();
                return agentTaskRepository.tryAcquireLease(taskId, "instance-b", now, leaseUntil);
            });
            startGate.countDown();
            boolean firstAcquired = first.get();
            boolean secondAcquired = second.get();

            assertThat((firstAcquired ? 1 : 0) + (secondAcquired ? 1 : 0)).isEqualTo(1);
            String leaseOwner = firstAcquired ? "instance-a" : "instance-b";
            assertThat(agentTaskRepository.renewLease(
                    taskId,
                    leaseOwner,
                    LocalDateTime.now().plusSeconds(60)
            )).isTrue();
            agentTaskRepository.releaseLease(taskId, leaseOwner);
            assertThat(agentTaskRepository.tryAcquireLease(
                    taskId,
                    "instance-after-release",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusSeconds(30)
            )).isTrue();
        }
    }

    /**
     * 验证项目、环境、OpenAPI 版本、幂等导入、失败记录和重试完整链路。
     */
    @Test
    void shouldManageProjectAndOpenApiCatalog() throws IOException {
        WebTestClient client = authenticatedClient();
        String projectCode = "stage-two-" + UUID.randomUUID().toString().replace("-", "");

        JsonNode project = client.post()
                .uri("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "code": "%s",
                          "name": "JApiServer",
                          "description": "阶段 2 集成测试"
                        }
                        """.formatted(projectCode))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(project).isNotNull();
        long projectId = project.path("data").path("id").asLong();

        client.post()
                .uri("/api/v1/projects/{projectId}/environments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "local",
                          "baseUrl": "http://localhost:8080/",
                          "defaultEnvironment": false
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.baseUrl").isEqualTo("http://localhost:8080")
                .jsonPath("$.data.defaultEnvironment").isEqualTo(true);

        client.post()
                .uri("/api/v1/projects/{projectId}/environments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "invalid",
                          "baseUrl": "file:///etc/passwd",
                          "defaultEnvironment": false
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PROJECT_400_001");

        byte[] yaml = new ClassPathResource("openapi/japiserver-stage2.yaml").getContentAsByteArray();
        JsonNode firstImport = uploadOpenApi(client, projectId, "japiserver.yaml", yaml)
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(firstImport).isNotNull();
        long importId = firstImport.path("data").path("id").asLong();
        assertThat(firstImport.path("data").path("endpointCount").asInt()).isEqualTo(4);
        assertThat(firstImport.path("data").path("schemaCount").asInt()).isEqualTo(3);
        assertThat(firstImport.path("data").path("securitySchemeCount").asInt()).isEqualTo(1);

        JsonNode duplicateImport = uploadOpenApi(client, projectId, "renamed.yaml", yaml)
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(duplicateImport).isNotNull();
        assertThat(duplicateImport.path("data").path("id").asLong()).isEqualTo(importId);

        client.get()
                .uri("/api/v1/projects/{projectId}/openapi/endpoints", projectId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(4)
                .jsonPath("$.data[0].path").exists()
                .jsonPath("$.data[2].parameters[0].name")
                .isEqualTo("domainId");

        byte[] invalidDocument = "title: 这不是 OpenAPI 文档".getBytes(StandardCharsets.UTF_8);
        uploadOpenApi(client, projectId, "invalid.yaml", invalidDocument)
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("OPENAPI_422_001")
                .jsonPath("$.message").value(message ->
                        assertThat(message.toString()).contains("导入记录 ID=")
                );

        JsonNode importsAfterFailure = client.get()
                .uri("/api/v1/projects/{projectId}/openapi/imports", projectId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(importsAfterFailure).isNotNull();
        assertThat(importsAfterFailure.path("data")).hasSize(2);
        JsonNode failed = findImportByStatus(importsAfterFailure.path("data"), "FAILED");

        client.post()
                .uri(
                        "/api/v1/projects/{projectId}/openapi/imports/{importId}/retry",
                        projectId,
                        failed.path("id").asLong()
                )
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("OPENAPI_422_001");

        client.get()
                .uri("/api/v1/projects/{projectId}/openapi/imports", projectId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(3)
                .jsonPath("$.data[0].retryOfId").isEqualTo(failed.path("id").asLong());

        client.delete()
                .uri("/api/v1/projects/{projectId}", projectId)
                .exchange()
                .expectStatus().isOk();
        client.get()
                .uri("/api/v1/projects/{projectId}", projectId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PROJECT_404_001");
    }

    /**
     * 验证文档上传、幂等索引、混合检索、来源引用与 Recall@1 评测链路。
     */
    @Test
    void shouldIndexAndEvaluateKnowledgeDocuments() {
        WebTestClient client = authenticatedClient();
        String projectCode = "stage-three-" + UUID.randomUUID().toString().replace("-", "");
        JsonNode project = client.post()
                .uri("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "code": "%s",
                          "name": "阶段 3 检索项目",
                          "description": "混合 RAG 公共设施集成测试"
                        }
                        """.formatted(projectCode))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(project).isNotNull();
        long projectId = project.path("data").path("id").asLong();

        byte[] loginGuide = """
                # 登录认证
                调用 /auth/login 并提交用户名和密码获取 JWT 访问令牌。
                后续请求需要在 Authorization 请求头中携带 Bearer Token。

                # 错误码
                AUTH_401 表示访问令牌无效或已过期。
                """.getBytes(StandardCharsets.UTF_8);
        byte[] orderGuide = """
                # 订单支付
                调用 /orders 创建订单，随后使用 /payments 完成支付。
                支付成功后订单状态变更为 PAID。

                # 错误码
                ORDER_409 表示订单状态不允许重复支付。
                """.getBytes(StandardCharsets.UTF_8);

        JsonNode loginDocument = uploadKnowledgeDocument(
                client, projectId, "login-guide.md", loginGuide
        )
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        JsonNode orderDocument = uploadKnowledgeDocument(
                client, projectId, "order-guide.md", orderGuide
        )
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(loginDocument).isNotNull();
        assertThat(orderDocument).isNotNull();
        long loginDocumentId = loginDocument.path("data").path("id").asLong();
        long orderDocumentId = orderDocument.path("data").path("id").asLong();
        assertThat(loginDocument.path("data").path("status").asText()).isEqualTo("INDEXED");
        assertThat(loginDocument.path("data").path("chunkCount").asInt()).isPositive();

        JsonNode duplicate = uploadKnowledgeDocument(
                client, projectId, "renamed-login.md", loginGuide
        )
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.path("data").path("id").asLong()).isEqualTo(loginDocumentId);

        client.post()
                .uri("/api/v1/projects/{projectId}/retrieval/search", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "query": "如何进行登录认证",
                          "topK": 2
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].documentId").isEqualTo(loginDocumentId)
                .jsonPath("$.data[0].vectorRank").exists()
                .jsonPath("$.data[0].citation").value(citation ->
                        assertThat(citation.toString()).contains("login-guide.md", "登录认证")
                );

        client.post()
                .uri("/api/v1/projects/{projectId}/retrieval/search", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "query": "/auth/login AUTH_401",
                          "topK": 2
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].documentId").isEqualTo(loginDocumentId)
                .jsonPath("$.data[0].keywordRank").exists();

        createEvaluationCase(
                client, projectId, "登录语义检索", "怎样获取访问令牌并完成认证", loginDocumentId
        );
        createEvaluationCase(
                client, projectId, "订单语义检索", "订单创建后如何完成支付", orderDocumentId
        );
        client.post()
                .uri("/api/v1/projects/{projectId}/retrieval/evaluations", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"topK\": 1}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.topK").isEqualTo(1)
                .jsonPath("$.data.caseCount").isEqualTo(2)
                .jsonPath("$.data.hitCount").isEqualTo(2)
                .jsonPath("$.data.recallAtK").isEqualTo(1.0)
                .jsonPath("$.data.details.length()").isEqualTo(2);

        client.delete()
                .uri("/api/v1/projects/{projectId}/documents/{documentId}", projectId, loginDocumentId)
                .exchange()
                .expectStatus().isOk();
        client.delete()
                .uri("/api/v1/projects/{projectId}/documents/{documentId}", projectId, orderDocumentId)
                .exchange()
                .expectStatus().isOk();
        client.delete()
                .uri("/api/v1/projects/{projectId}", projectId)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 验证登录取值、Bearer 传递、响应断言、审计脱敏和危险目标拦截。
     */
    @Test
    void shouldExecuteControlledMultiStepScenario() {
        WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        try {
            wireMock.stubFor(post(urlEqualTo("/auth/login"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "token": "stage4-secret-token",
                                        "userId": 7
                                      }
                                    }
                                    """)));
            wireMock.stubFor(get(urlPathEqualTo("/users/7"))
                    .withQueryParam("verbose", equalTo("true"))
                    .withHeader("Authorization", equalTo("Bearer stage4-secret-token"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": 7,
                                      "name": "tester",
                                      "roles": ["USER", "ADMIN"],
                                      "active": true
                                    }
                                    """)));

            WebTestClient client = authenticatedClient();
            String projectCode = "stage-four-" + UUID.randomUUID().toString().replace("-", "");
            JsonNode project = client.post()
                    .uri("/api/v1/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "code": "%s",
                              "name": "阶段 4 执行项目",
                              "description": "受控 HTTP 执行集成测试"
                            }
                            """.formatted(projectCode))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(project).isNotNull();
            long projectId = project.path("data").path("id").asLong();

            JsonNode environment = client.post()
                    .uri("/api/v1/projects/{projectId}/environments", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "name": "wiremock",
                              "baseUrl": "%s",
                              "allowedMethods": "GET,POST,DELETE",
                              "allowPrivateNetwork": true,
                              "defaultEnvironment": true
                            }
                            """.formatted(wireMock.baseUrl()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(environment).isNotNull();
            long environmentId = environment.path("data").path("id").asLong();

            uploadOpenApi(client, projectId, "execution-openapi.yaml", EXECUTION_OPENAPI)
                    .expectStatus().isCreated();

            JsonNode execution = client.post()
                    .uri("/api/v1/projects/{projectId}/executions", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "environmentId": %d,
                              "initialVariables": {
                                "userId": 7
                              },
                              "steps": [
                                {
                                  "name": "登录获取令牌",
                                  "method": "POST",
                                  "path": "/auth/login",
                                  "body": {
                                    "username": "tester",
                                    "password": "plain-password"
                                  },
                                  "extractors": [
                                    {
                                      "name": "accessToken",
                                      "jsonPath": "$.data.token"
                                    }
                                  ],
                                  "assertions": [
                                    {
                                      "type": "STATUS_CODE",
                                      "expectedValue": 200
                                    },
                                    {
                                      "type": "FIELD_TYPE",
                                      "jsonPath": "$.data.token",
                                      "expectedType": "STRING"
                                    }
                                  ]
                                },
                                {
                                  "name": "查询当前用户",
                                  "method": "GET",
                                  "path": "/users/{userId}",
                                  "pathVariables": {
                                    "userId": "{{userId}}"
                                  },
                                  "queryParams": {
                                    "verbose": "true"
                                  },
                                  "authentication": {
                                    "type": "BEARER",
                                    "token": "{{accessToken}}"
                                  },
                                  "assertions": [
                                    {
                                      "type": "STATUS_CODE",
                                      "expectedValue": 200
                                    },
                                    {
                                      "type": "FIELD_EQUALS",
                                      "jsonPath": "$.id",
                                      "expectedValue": 7
                                    },
                                    {
                                      "type": "FIELD_CONTAINS",
                                      "jsonPath": "$.roles",
                                      "expectedValue": "ADMIN"
                                    }
                                  ]
                                }
                              ]
                            }
                            """.formatted(environmentId))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(execution).isNotNull();
            assertThat(execution.path("data").path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(execution.path("data").path("completedStepCount").asInt()).isEqualTo(2);
            assertThat(execution.path("data").path("steps").path(0)
                    .path("extractedVariables").path("accessToken").asText()).isEqualTo("******");
            assertThat(execution.path("data").path("steps").path(0)
                    .path("responseBody").path("data").path("token").asText()).isEqualTo("******");
            long executionId = execution.path("data").path("id").asLong();

            wireMock.verify(postRequestedFor(urlEqualTo("/auth/login")));
            wireMock.verify(getRequestedFor(urlPathEqualTo("/users/7"))
                    .withHeader("Authorization", equalTo("Bearer stage4-secret-token")));

            Integer secretCount = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM api_execution_step
                            WHERE execution_id = ?
                              AND CONCAT_WS(
                                  '',
                                  request_headers_json,
                                  request_body_redacted,
                                  response_headers_json,
                                  response_body_redacted,
                                  assertions_json
                              ) LIKE '%stage4-secret-token%'
                            """,
                    Integer.class,
                    executionId
            );
            assertThat(secretCount).isZero();

            client.get()
                    .uri(
                            "/api/v1/projects/{projectId}/executions/{executionId}",
                            projectId,
                            executionId
                    )
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.status").isEqualTo("SUCCEEDED")
                    .jsonPath("$.data.steps[0].extractedVariables.accessToken")
                    .isEqualTo("******");

            JsonNode blockedEnvironment = client.post()
                    .uri("/api/v1/projects/{projectId}/environments", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "name": "blocked-private",
                              "baseUrl": "%s",
                              "allowedMethods": "GET",
                              "allowPrivateNetwork": false,
                              "defaultEnvironment": false
                            }
                            """.formatted(wireMock.baseUrl()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(blockedEnvironment).isNotNull();

            client.post()
                    .uri("/api/v1/projects/{projectId}/executions", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "environmentId": %d,
                              "steps": [
                                {
                                  "name": "私网拦截",
                                  "method": "GET",
                                  "path": "/blocked"
                                }
                              ]
                            }
                            """.formatted(blockedEnvironment.path("data").path("id").asLong()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.data.status").isEqualTo("FAILED")
                    .jsonPath("$.data.errorCode").isEqualTo("EXECUTOR_403_002");
            wireMock.verify(0, getRequestedFor(urlEqualTo("/blocked")));

            client.post()
                    .uri("/api/v1/projects/{projectId}/executions", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "environmentId": %d,
                              "steps": [
                                {
                                  "name": "危险删除",
                                  "method": "DELETE",
                                  "path": "/users/7",
                                  "dangerousOperationConfirmed": false
                                }
                              ]
                            }
                            """.formatted(environmentId))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.data.status").isEqualTo("FAILED")
                    .jsonPath("$.data.errorCode").isEqualTo("EXECUTOR_409_002");
            wireMock.verify(0, deleteRequestedFor(urlEqualTo("/users/7")));

            client.delete()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .exchange()
                    .expectStatus().isOk();
        } finally {
            wireMock.stop();
        }
    }

    /**
     * 验证 Agent 的检索、规划、工具执行、事件、报告、脱敏和危险确认闭环。
     */
    @Test
    void shouldRunPersistentAgentTaskWithConfirmation() throws Exception {
        WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        try {
            wireMock.stubFor(post(urlEqualTo("/agent/login"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "token": "stage5-agent-secret",
                                        "userId": 9
                                      }
                                    }
                                    """)));
            wireMock.stubFor(get(urlEqualTo("/agent/users/9"))
                    .withHeader("Authorization", equalTo("Bearer stage5-agent-secret"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": 9,
                                      "name": "agent-user",
                                      "active": true
                                    }
                                    """)));
            wireMock.stubFor(delete(urlEqualTo("/agent/users/9"))
                    .willReturn(aResponse().withStatus(204)));

            WebTestClient client = authenticatedClient();
            String projectCode = "stage-five-" + UUID.randomUUID().toString().replace("-", "");
            JsonNode project = client.post()
                    .uri("/api/v1/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "code": "%s",
                              "name": "阶段 5 Agent 项目",
                              "description": "Agent 状态机和工具调用集成测试"
                            }
                            """.formatted(projectCode))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(project).isNotNull();
            long projectId = project.path("data").path("id").asLong();

            JsonNode environment = client.post()
                    .uri("/api/v1/projects/{projectId}/environments", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "name": "agent-wiremock",
                              "baseUrl": "%s",
                              "allowedMethods": "GET,POST,DELETE",
                              "allowPrivateNetwork": true,
                              "defaultEnvironment": true
                            }
                            """.formatted(wireMock.baseUrl()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(environment).isNotNull();
            long environmentId = environment.path("data").path("id").asLong();

            uploadOpenApi(client, projectId, "agent-openapi.yaml", EXECUTION_OPENAPI)
                    .expectStatus().isCreated();

            JsonNode created = client.post()
                    .uri("/api/v1/projects/{projectId}/agent-tasks", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "environmentId": %d,
                              "goal": "登录并查询用户，验证账号处于启用状态",
                              "planHint": [
                                {
                                  "name": "登录获取令牌",
                                  "method": "POST",
                                  "path": "/agent/login",
                                  "body": {
                                    "username": "agent-user",
                                    "password": "stage5-plain-password"
                                  },
                                  "extractors": [
                                    {
                                      "name": "accessToken",
                                      "jsonPath": "$.data.token"
                                    },
                                    {
                                      "name": "userId",
                                      "jsonPath": "$.data.userId"
                                    }
                                  ],
                                  "assertions": [
                                    {
                                      "type": "STATUS_CODE",
                                      "expectedValue": 200
                                    }
                                  ]
                                },
                                {
                                  "name": "查询登录用户",
                                  "method": "GET",
                                  "path": "/agent/users/{userId}",
                                  "pathVariables": {
                                    "userId": "{{userId}}"
                                  },
                                  "authentication": {
                                    "type": "BEARER",
                                    "token": "{{accessToken}}"
                                  },
                                  "assertions": [
                                    {
                                      "type": "FIELD_EQUALS",
                                      "jsonPath": "$.active",
                                      "expectedValue": true
                                    }
                                  ]
                                }
                              ]
                            }
                            """.formatted(environmentId))
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(created).isNotNull();
            long taskId = created.path("data").path("id").asLong();
            JsonNode completed = waitForAgentStatus(
                    client,
                    projectId,
                    taskId,
                    "SUCCEEDED",
                    Duration.ofSeconds(20)
            );
            assertThat(completed.path("data").path("toolCallCount").asInt()).isEqualTo(3);
            assertThat(completed.path("data").path("resultSummary").asText())
                    .contains("2 个步骤", "通过 2 个", "失败 0 个");
            assertThat(completed.path("data").path("toolCalls").toString())
                    .contains("searchApiDocument", "executeHttpRequest", "generateTestReport")
                    .doesNotContain("stage5-agent-secret", "stage5-plain-password");
            wireMock.verify(postRequestedFor(urlEqualTo("/agent/login")));
            wireMock.verify(getRequestedFor(urlEqualTo("/agent/users/9"))
                    .withHeader("Authorization", equalTo("Bearer stage5-agent-secret")));

            JsonNode reports = client.get()
                    .uri("/api/v1/projects/{projectId}/reports", projectId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(reports).isNotNull();
            assertThat(reports.path("data")).hasSize(1);
            long reportId = reports.path("data").path(0).path("id").asLong();
            JsonNode report = client.get()
                    .uri(
                            "/api/v1/projects/{projectId}/reports/{reportId}",
                            projectId,
                            reportId
                    )
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(report).isNotNull();
            assertThat(report.path("data").path("taskId").asLong()).isEqualTo(taskId);
            assertThat(report.path("data").path("executionId").asLong()).isPositive();
            assertThat(report.path("data").path("totalSteps").asInt()).isEqualTo(2);
            assertThat(report.path("data").path("passedSteps").asInt()).isEqualTo(2);
            assertThat(report.path("data").path("steps")).hasSize(2);
            assertThat(report.path("data").toString())
                    .doesNotContain("stage5-agent-secret", "stage5-plain-password");

            JsonNode events = client.get()
                    .uri(
                            "/api/v1/projects/{projectId}/agent-tasks/{taskId}/events?after=0",
                            projectId,
                            taskId
                    )
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(events).isNotNull();
            assertThat(events.path("data").toString())
                    .contains(
                            "RETRIEVING",
                            "PLANNING",
                            "EXECUTING",
                            "OBSERVING",
                            "REPORTING",
                            "SUCCEEDED"
                    )
                    .doesNotContain("stage5-agent-secret", "stage5-plain-password");
            String eventStream = client.get()
                    .uri(
                            "/api/v1/projects/{projectId}/agent-tasks/{taskId}/stream?after=0",
                            projectId,
                            taskId
                    )
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(eventStream)
                    .contains("event:agent-event", "TASK_CREATED", "TASK_COMPLETED")
                    .doesNotContain("stage5-agent-secret", "stage5-plain-password");
            long lastSequence = events.path("data")
                    .path(events.path("data").size() - 1)
                    .path("sequenceNo")
                    .asLong();
            String resumedStream = client.get()
                    .uri(
                            "/api/v1/projects/{projectId}/agent-tasks/{taskId}/stream?after={after}",
                            projectId,
                            taskId,
                            lastSequence
                    )
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(resumedStream).isNullOrEmpty();
            wireMock.verify(1, postRequestedFor(urlEqualTo("/agent/login")));

            Integer leakedCount = jdbcTemplate.queryForObject(
                    """
                            SELECT (
                                SELECT COUNT(*)
                                FROM agent_task
                                WHERE id = ?
                                  AND CONCAT_WS(
                                      '',
                                      goal,
                                      plan_json,
                                      context_json_redacted,
                                      result_summary,
                                      error_message
                                  ) REGEXP 'stage5-agent-secret|stage5-plain-password'
                            ) + (
                                SELECT COUNT(*)
                                FROM agent_tool_call
                                WHERE task_id = ?
                                  AND CONCAT_WS(
                                      '',
                                      request_json_redacted,
                                      response_json_redacted,
                                      error_message
                                  ) REGEXP 'stage5-agent-secret|stage5-plain-password'
                            ) + (
                                SELECT COUNT(*)
                                FROM agent_model_call
                                WHERE task_id = ?
                                  AND CONCAT_WS(
                                      '',
                                      model_name,
                                      error_code,
                                      error_message
                                  ) REGEXP 'stage5-agent-secret|stage5-plain-password'
                            ) + (
                                SELECT COUNT(*)
                                FROM agent_task_event
                                WHERE task_id = ?
                                  AND payload_json
                                      REGEXP 'stage5-agent-secret|stage5-plain-password'
                            ) + (
                                SELECT COUNT(*)
                                FROM test_report
                                WHERE task_id = ?
                                  AND CONCAT_WS(
                                      '',
                                      title,
                                      summary,
                                      evidence_json,
                                      metrics_json
                                  ) REGEXP 'stage5-agent-secret|stage5-plain-password'
                            ) + (
                                SELECT COUNT(*)
                                FROM test_report_step
                                WHERE report_id = ?
                                  AND CONCAT_WS(
                                      '',
                                      request_headers_json,
                                      request_body_redacted,
                                      response_headers_json,
                                      response_body_redacted,
                                      assertions_json,
                                      error_message
                                  ) REGEXP 'stage5-agent-secret|stage5-plain-password'
                            )
                            """,
                    Integer.class,
                    taskId,
                    taskId,
                    taskId,
                    taskId,
                    taskId,
                    reportId
            );
            assertThat(leakedCount).isZero();

            JsonNode dangerous = client.post()
                    .uri("/api/v1/projects/{projectId}/agent-tasks", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "environmentId": %d,
                              "goal": "删除测试用户",
                              "planHint": [
                                {
                                  "name": "删除测试用户",
                                  "method": "DELETE",
                                  "path": "/agent/users/9",
                                  "assertions": [
                                    {
                                      "type": "STATUS_CODE",
                                      "expectedValue": 204
                                    }
                                  ]
                                }
                              ]
                            }
                            """.formatted(environmentId))
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(dangerous).isNotNull();
            long dangerousTaskId = dangerous.path("data").path("id").asLong();
            JsonNode waiting = waitForAgentStatus(
                    client,
                    projectId,
                    dangerousTaskId,
                    "WAITING_CONFIRMATION",
                    Duration.ofSeconds(20)
            );
            assertThat(waiting.path("data").path("confirmation").path("status").asText())
                    .isEqualTo("PENDING");
            wireMock.verify(0, deleteRequestedFor(urlEqualTo("/agent/users/9")));

            client.post()
                    .uri(
                            "/api/v1/projects/{projectId}/agent-tasks/{taskId}/confirmation",
                            projectId,
                            dangerousTaskId
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "approved": true,
                              "note": "仅批准删除 WireMock 测试数据"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();
            waitForAgentStatus(
                    client,
                    projectId,
                    dangerousTaskId,
                    "SUCCEEDED",
                    Duration.ofSeconds(20)
            );
            wireMock.verify(1, deleteRequestedFor(urlEqualTo("/agent/users/9")));

            client.delete()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .exchange()
                    .expectStatus().isOk();
        } finally {
            wireMock.stop();
        }
    }

    private JsonNode waitForAgentStatus(
            WebTestClient client,
            long projectId,
            long taskId,
            String expectedStatus,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            latest = client.get()
                    .uri(
                            "/api/v1/projects/{projectId}/agent-tasks/{taskId}",
                            projectId,
                            taskId
                    )
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JsonNode.class)
                    .returnResult()
                    .getResponseBody();
            if (latest != null
                    && expectedStatus.equals(latest.path("data").path("status").asText())) {
                return latest;
            }
            if (latest != null) {
                String status = latest.path("data").path("status").asText();
                if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                    throw new AssertionError("Agent 提前进入终态：" + latest);
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("等待 Agent 状态超时，最后响应：" + latest);
    }

    private WebTestClient authenticatedClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private long createProject(WebTestClient client, String code, String name) {
        JsonNode project = client.post()
                .uri("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "code": "%s",
                          "name": "%s"
                        }
                        """.formatted(code, name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(project).isNotNull();
        return project.path("data").path("id").asLong();
    }

    private WebTestClient.ResponseSpec uploadOpenApi(
            WebTestClient client,
            long projectId,
            String fileName,
            byte[] content
    ) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        return client.post()
                .uri("/api/v1/projects/{projectId}/openapi/imports", projectId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    private WebTestClient.ResponseSpec uploadKnowledgeDocument(
            WebTestClient client,
            long projectId,
            String fileName,
            byte[] content
    ) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                })
                .contentType(MediaType.TEXT_MARKDOWN);
        return client.post()
                .uri("/api/v1/projects/{projectId}/documents", projectId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    private void createEvaluationCase(
            WebTestClient client,
            long projectId,
            String name,
            String query,
            long expectedDocumentId
    ) {
        client.post()
                .uri("/api/v1/projects/{projectId}/retrieval/evaluation-cases", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "%s",
                          "query": "%s",
                          "expectedDocumentId": %d
                        }
                        """.formatted(name, query, expectedDocumentId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.expectedDocumentId").isEqualTo(expectedDocumentId);
    }

    private JsonNode findImportByStatus(JsonNode imports, String status) {
        for (JsonNode value : imports) {
            if (status.equals(value.path("status").asText())) {
                return value;
            }
        }
        throw new AssertionError("未找到状态为 " + status + " 的导入记录");
    }
}
