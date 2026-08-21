package com.dochelper.executor.application;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.domain.OperationRisk;
import com.dochelper.executor.domain.ResolvedOperation;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import org.springframework.stereotype.Component;

/**
 * 将每个待执行请求绑定到当前 OpenAPI 目录，并执行风险分级。
 */
@Component
public class EndpointExecutionPolicy {

    private final OpenApiCatalogRepository catalogRepository;

    public EndpointExecutionPolicy(OpenApiCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /**
     * 解析并校验接口目录、方法与服务端确认凭据。
     */
    public ResolvedOperation validate(
            Long projectId,
            ExecutionStepRequest step,
            boolean serverConfirmed
    ) {
        String method = step.method().trim().toUpperCase(Locale.ROOT);
        String candidatePath = normalizePath(step.path());
        List<ApiEndpoint> samePath = catalogRepository.findEndpoints(projectId, null).stream()
                .filter(endpoint -> matches(endpoint.path(), candidatePath))
                .toList();
        if (samePath.isEmpty()) {
            throw new BusinessException(
                    ExecutionErrorCode.ENDPOINT_NOT_IN_CATALOG,
                    "请求路径不在当前 OpenAPI 目录：" + candidatePath
            );
        }
        ApiEndpoint endpoint = samePath.stream()
                .filter(item -> method.equalsIgnoreCase(item.httpMethod()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ExecutionErrorCode.ENDPOINT_METHOD_MISMATCH,
                        "OpenAPI 目录中的路径不支持 " + method + " 方法"
                ));
        OperationRisk risk = classify(method, endpoint.path(), endpoint.operationId());
        if (risk.requiresConfirmation() && !serverConfirmed) {
            throw new BusinessException(
                    ExecutionErrorCode.WRITE_CONFIRMATION_REQUIRED,
                    method + " " + endpoint.path() + " 必须由项目 Owner 确认"
            );
        }
        return new ResolvedOperation(
                endpoint.id(), endpoint.operationId(), method, endpoint.path(), risk
        );
    }

    private OperationRisk classify(String method, String path, String operationId) {
        if (List.of("GET", "HEAD", "OPTIONS").contains(method)) {
            return OperationRisk.READ_ONLY;
        }
        if ("DELETE".equals(method)) {
            return OperationRisk.DESTRUCTIVE;
        }
        String semantic = ((path == null ? "" : path) + " "
                + (operationId == null ? "" : operationId)).toLowerCase(Locale.ROOT);
        if ("POST".equals(method)
                && (semantic.contains("/login") || semantic.contains("/auth/token")
                || semantic.contains("signin"))) {
            return OperationRisk.SAFE_AUTH;
        }
        return OperationRisk.MUTATING;
    }

    private String normalizePath(String value) {
        try {
            String path = value == null ? "" : value.trim();
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%2e")) {
                throw new IllegalArgumentException("路径包含歧义编码");
            }
            // OpenAPI 路径模板允许使用花括号，URI 解析前先进行等价编码。
            URI uri = URI.create(path.replace("{", "%7B").replace("}", "%7D"));
            if (uri.isAbsolute() || uri.getRawQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("路径必须为不含查询串的相对目标");
            }
            String normalized = uri.normalize().getPath();
            if (!normalized.startsWith("/") || normalized.contains("..")) {
                throw new IllegalArgumentException("路径不安全");
            }
            return normalized.length() > 1 && normalized.endsWith("/")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ExecutionErrorCode.TARGET_BLOCKED, "请求路径无法安全标准化");
        }
    }

    private boolean matches(String template, String candidate) {
        String normalizedTemplate = normalizePath(template);
        String[] segments = normalizedTemplate.split("/", -1);
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                regex.append('/');
            }
            String segment = segments[index];
            if (segment.startsWith("{") && segment.endsWith("}")) {
                regex.append("[^/]+");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString()).matcher(candidate).matches();
    }
}
