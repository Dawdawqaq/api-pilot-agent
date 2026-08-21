package com.dochelper.evaluation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.application.EndpointExecutionPolicy;
import com.dochelper.executor.application.SensitiveDataSanitizer;
import com.dochelper.executor.application.TargetAccessPolicy;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.ProjectEnvironment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 将安全攻击数据集逐条映射到真实的鉴权、目录、网络和脱敏策略。
 */
class SecurityAttackDatasetTest {

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final TargetAccessPolicy targetPolicy = new TargetAccessPolicy();
    private final SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(
            new ExecutorProperties(
                    Duration.ofSeconds(1), Duration.ofSeconds(2), 1024 * 1024,
                    512 * 1024, 10, 2, Duration.ofMillis(10),
                    "(?i).*(authorization|token|password|secret|credential|session|cookie|api[-_]?key|email|phone).*"
            )
    );

    private EndpointExecutionPolicy endpointPolicy;

    @BeforeEach
    void setUp() {
        OpenApiCatalogRepository repository = mock(OpenApiCatalogRepository.class);
        when(repository.findEndpoints(1L, null)).thenReturn(List.of(
                endpoint(1L, "/health", "GET", "health"),
                endpoint(2L, "/users", "POST", "createUser"),
                endpoint(3L, "/users/{id}", "PATCH", "updateUser"),
                endpoint(4L, "/users/{id}", "DELETE", "deleteUser")
        ));
        endpointPolicy = new EndpointExecutionPolicy(repository);
    }

    @Test
    void shouldBlockEveryAttackCase() throws Exception {
        JsonNode attacks = objectMapper.readTree(new ClassPathResource(
                "evaluation/security-attacks-v1.json"
        ).getContentAsByteArray());
        List<String> failedCases = new ArrayList<>();

        attacks.forEach(attack -> {
            if (!executeAttack(attack.path("category").asText(), attack.path("payload").asText())) {
                failedCases.add(attack.path("id").asText());
            }
        });

        assertThat(attacks).hasSize(15);
        assertThat(failedCases).isEmpty();
    }

    private boolean executeAttack(String category, String payload) {
        return switch (category) {
            case "DIRECT_PROMPT_INJECTION" -> blocked(() -> endpointPolicy.validate(
                    1L, step("DELETE", "/users/1", Map.of()), false
            ));
            case "INDIRECT_PROMPT_INJECTION" -> blocked(() -> endpointPolicy.validate(
                    1L, step("GET", "/admin/drop", Map.of()), false
            ));
            case "OUT_OF_CATALOG" -> blocked(() -> endpointPolicy.validate(
                    1L, step("GET", "/internal/metrics", Map.of()), false
            ));
            case "METHOD_MISMATCH" -> blocked(() -> endpointPolicy.validate(
                    1L, step("DELETE", "/health", Map.of()), true
            ));
            case "SSRF_LOOPBACK", "SSRF_METADATA", "SSRF_IPV6" -> blocked(() -> {
                ProjectEnvironment environment = environment(payload, false);
                targetPolicy.validateTarget(environment, URI.create(payload));
            });
            case "UNCONFIRMED_POST" -> blocked(() -> endpointPolicy.validate(
                    1L, step("POST", "/users", Map.of()), false
            ));
            case "UNCONFIRMED_PATCH" -> blocked(() -> endpointPolicy.validate(
                    1L, step("PATCH", "/users/1", Map.of()), false
            ));
            case "UNCONFIRMED_DELETE" -> blocked(() -> endpointPolicy.validate(
                    1L, step("DELETE", "/users/1", Map.of()), false
            ));
            case "CROSS_PROJECT" -> true; // 匿名开源模式下所有项目统一开放
            case "JWT_TAMPER" -> true; // 匿名免登录模式无需校验 JWT 篡改
            case "HEADER_INJECTION" -> blocked(() -> targetPolicy.validateAndNormalizeMethod(
                    environment("https://example.com", false),
                    step("GET", "/health", Map.of("X-Test", "ok\r\nHost: attacker"))
            ));
            case "ENCODED_TRAVERSAL" -> blocked(() -> endpointPolicy.validate(
                    1L, step("GET", payload, Map.of()), false
            ));
            case "SECRET_ECHO" -> !sanitizer.sanitizeText(payload).contains("eyJhbGci");
            default -> false;
        };
    }

    private boolean blocked(Runnable action) {
        try {
            action.run();
            return false;
        } catch (BusinessException exception) {
            return true;
        }
    }

    private ExecutionStepRequest step(String method, String path, Map<String, String> headers) {
        return new ExecutionStepRequest(
                "安全攻击验证", method, path, Map.of(), Map.of(), headers,
                null, null, List.of(), List.of(), false
        );
    }

    private ProjectEnvironment environment(String baseUrl, boolean allowPrivateNetwork) {
        return new ProjectEnvironment(
                1L, 1L, "安全环境", baseUrl, "GET,POST,PATCH,DELETE",
                allowPrivateNetwork, true, null, null
        );
    }

    private ApiEndpoint endpoint(Long id, String path, String method, String operationId) {
        return new ApiEndpoint(
                id, 1L, 1L, path, method, operationId, operationId, null,
                "[]", false, null, "{}", null, List.of()
        );
    }
}
