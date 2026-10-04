package com.dochelper.system.application;

import java.net.URI;
import java.util.ArrayList;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.application.TargetAccessPolicy;
import com.dochelper.governance.application.ProjectModelPolicyService;
import com.dochelper.model.application.LlmConfigurationService;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.ProjectStatus;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import com.dochelper.system.api.vo.WorkspaceReadinessResponse;
import com.dochelper.system.api.vo.WorkspaceReadinessResponse.Check;
import org.springframework.stereotype.Service;

/** 汇总提交前条件；不发送 HTTP 请求，不消耗模型额度。 */
@Service
public class WorkspaceReadinessService {
    private final ApiProjectRepository projects;
    private final ProjectEnvironmentRepository environments;
    private final OpenApiCatalogRepository catalog;
    private final LlmConfigurationService models;
    private final ProjectModelPolicyService policies;
    private final TargetAccessPolicy targets;

    public WorkspaceReadinessService(ApiProjectRepository projects, ProjectEnvironmentRepository environments,
            OpenApiCatalogRepository catalog, LlmConfigurationService models,
            ProjectModelPolicyService policies, TargetAccessPolicy targets) {
        this.projects = projects;
        this.environments = environments;
        this.catalog = catalog;
        this.models = models;
        this.policies = policies;
        this.targets = targets;
    }

    public WorkspaceReadinessResponse check(Long projectId, Long environmentId) {
        var project = projects.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
        var checks = new ArrayList<Check>();
        checks.add(new Check("project", "项目", project.status() == ProjectStatus.ACTIVE ? "READY" : "BLOCKED",
                project.status() == ProjectStatus.ACTIVE ? project.name() : "项目已归档，请先恢复为启用", "projects"));
        var model = models.getConfiguration();
        boolean api = "API".equals(model.mode());
        checks.add(new Check("model", "规划模型", api && !model.apiKeyConfigured() ? "BLOCKED" : "READY",
                api ? model.activeModel() + " · 已配置，连接尚未验证" : "离线规划 · 适合开发验证", "settings"));
        var endpoints = catalog.findEndpoints(projectId, project.currentImportId());
        checks.add(new Check("catalog", "接口资料", endpoints.isEmpty() ? "BLOCKED" : "READY",
                endpoints.isEmpty() ? "请先导入有效的 OpenAPI 文档" : "当前版本包含 " + endpoints.size() + " 个接口", "openapi"));
        var policy = policies.get(projectId);
        boolean allowed = !api || (policy.externalModelAllowed() && ("ANY".equalsIgnoreCase(policy.allowedProvider())
                || model.provider().equalsIgnoreCase(policy.allowedProvider())));
        checks.add(new Check("policy", "模型资料策略", allowed ? "READY" : "BLOCKED",
                !allowed ? "当前策略禁止调用所选模型供应商" : "接口结构" + (policy.allowSchemaContent() ? "允许" : "禁止")
                        + "发送 · 业务正文" + (policy.allowDocumentContent() ? "允许" : "禁止") + "发送", "settings"));
        var environment = environmentId == null ? java.util.Optional.<com.dochelper.project.domain.ProjectEnvironment>empty()
                : environments.findByIdAndProjectId(environmentId, projectId);
        if (environment.isEmpty()) {
            checks.add(new Check("environment", "执行环境", "BLOCKED", "请选择当前项目的执行环境", "projects"));
        } else {
            try {
                targets.validateTarget(environment.get(), URI.create(environment.get().baseUrl()));
                checks.add(new Check("environment", "执行环境", "READY", "地址通过访问策略校验 · 服务连通性尚未探测", "projects"));
            } catch (BusinessException | IllegalArgumentException exception) {
                checks.add(new Check("environment", "执行环境", "BLOCKED", exception.getMessage(), "projects"));
            }
        }
        checks.add(new Check("knowledge", "业务知识库", "OPTIONAL", "可选；没有业务文档也能测试接口", "knowledge"));
        return new WorkspaceReadinessResponse(checks.stream().noneMatch(item -> "BLOCKED".equals(item.status())), checks);
    }
}
