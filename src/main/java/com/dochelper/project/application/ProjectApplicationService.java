package com.dochelper.project.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.project.api.dto.CreateEnvironmentRequest;
import com.dochelper.project.api.dto.CreateProjectRequest;
import com.dochelper.project.api.dto.UpdateEnvironmentRequest;
import com.dochelper.project.api.dto.UpdateProjectRequest;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.ProjectEnvironment;
import com.dochelper.project.domain.ProjectStatus;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 被测项目及环境应用服务。
 */
@Service
public class ProjectApplicationService {

    private final ApiProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;

    public ProjectApplicationService(
            ApiProjectRepository projectRepository,
            ProjectEnvironmentRepository environmentRepository
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
    }

    /**
     * 创建被测项目。
     *
     * @param request 创建请求
     * @return 新项目
     */
    @Transactional
    public ApiProject createProject(CreateProjectRequest request, Long ownerUserId) {
        String normalizedCode = request.code().trim().toLowerCase(Locale.ROOT);
        projectRepository.findByCode(normalizedCode).ifPresent(project -> {
            throw new BusinessException(ProjectErrorCode.PROJECT_CODE_CONFLICT);
        });
        return projectRepository.save(new ApiProject(
                null,
                normalizedCode,
                request.name().trim(),
                trimToNull(request.description()),
                ProjectStatus.ACTIVE,
                null,
                null,
                null
        ));
    }

    public List<ApiProject> listProjects(Long userId) {
        return projectRepository.findAll();
    }

    public ApiProject getProject(Long projectId) {
        return requireProject(projectId);
    }

    /**
     * 更新项目基本信息，项目编码作为稳定业务标识不可修改。
     */
    @Transactional
    public ApiProject updateProject(Long projectId, UpdateProjectRequest request) {
        ApiProject current = requireProject(projectId);
        projectRepository.update(new ApiProject(
                current.id(),
                current.code(),
                request.name().trim(),
                trimToNull(request.description()),
                request.status(),
                current.currentImportId(),
                current.createdAt(),
                current.updatedAt()
        ));
        return requireProject(projectId);
    }

    @Transactional
    public boolean deleteProject(Long projectId) {
        requireProject(projectId);
        projectRepository.deleteById(projectId);
        return true;
    }

    /**
     * 创建项目环境。首个环境自动成为默认环境。
     */
    @Transactional
    public ProjectEnvironment createEnvironment(Long projectId, CreateEnvironmentRequest request) {
        requireProject(projectId);
        ensureEnvironmentNameAvailable(projectId, request.name(), null);
        List<ProjectEnvironment> existing = environmentRepository.findByProjectId(projectId);
        boolean defaultEnvironment = request.defaultEnvironment() || existing.isEmpty();
        if (defaultEnvironment) {
            environmentRepository.clearDefault(projectId, null);
        }
        return environmentRepository.save(new ProjectEnvironment(
                null,
                projectId,
                request.name().trim(),
                normalizeBaseUrl(request.baseUrl()),
                normalizeAllowedMethods(request.allowedMethods()),
                Boolean.TRUE.equals(request.allowPrivateNetwork()),
                defaultEnvironment,
                null,
                null
        ));
    }

    public List<ProjectEnvironment> listEnvironments(Long projectId) {
        requireProject(projectId);
        return environmentRepository.findByProjectId(projectId);
    }

    /**
     * 更新项目环境，并保证存在环境时始终恰好有一个默认环境。
     */
    @Transactional
    public ProjectEnvironment updateEnvironment(
            Long projectId,
            Long environmentId,
            UpdateEnvironmentRequest request
    ) {
        requireProject(projectId);
        ProjectEnvironment current = requireEnvironment(projectId, environmentId);
        ensureEnvironmentNameAvailable(projectId, request.name(), environmentId);

        boolean defaultEnvironment = request.defaultEnvironment();
        if (defaultEnvironment) {
            environmentRepository.clearDefault(projectId, environmentId);
        } else if (current.defaultEnvironment()) {
            List<ProjectEnvironment> alternatives = environmentRepository.findByProjectId(projectId)
                    .stream()
                    .filter(environment -> !environment.id().equals(environmentId))
                    .toList();
            if (alternatives.isEmpty()) {
                defaultEnvironment = true;
            } else {
                ProjectEnvironment replacement = alternatives.getFirst();
                environmentRepository.clearDefault(projectId, replacement.id());
                environmentRepository.update(new ProjectEnvironment(
                        replacement.id(),
                        replacement.projectId(),
                        replacement.name(),
                        replacement.baseUrl(),
                        replacement.allowedMethods(),
                        replacement.allowPrivateNetwork(),
                        true,
                        replacement.createdAt(),
                        replacement.updatedAt()
                ));
            }
        }

        environmentRepository.update(new ProjectEnvironment(
                current.id(),
                current.projectId(),
                request.name().trim(),
                normalizeBaseUrl(request.baseUrl()),
                normalizeAllowedMethods(request.allowedMethods()),
                Boolean.TRUE.equals(request.allowPrivateNetwork()),
                defaultEnvironment,
                current.createdAt(),
                current.updatedAt()
        ));
        return requireEnvironment(projectId, environmentId);
    }

    /**
     * 删除环境；若删除默认环境，则自动提升剩余的第一个环境。
     */
    @Transactional
    public boolean deleteEnvironment(Long projectId, Long environmentId) {
        requireProject(projectId);
        ProjectEnvironment current = requireEnvironment(projectId, environmentId);
        environmentRepository.deleteById(environmentId);
        if (current.defaultEnvironment()) {
            environmentRepository.findByProjectId(projectId).stream().findFirst().ifPresent(replacement ->
                    environmentRepository.update(new ProjectEnvironment(
                            replacement.id(),
                            replacement.projectId(),
                        replacement.name(),
                        replacement.baseUrl(),
                        replacement.allowedMethods(),
                        replacement.allowPrivateNetwork(),
                        true,
                            replacement.createdAt(),
                            replacement.updatedAt()
                    ))
            );
        }
        return true;
    }

    private ApiProject requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectEnvironment requireEnvironment(Long projectId, Long environmentId) {
        return environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ENVIRONMENT_NOT_FOUND));
    }

    private void ensureEnvironmentNameAvailable(Long projectId, String name, Long currentId) {
        environmentRepository.findByName(projectId, name.trim()).ifPresent(environment -> {
            if (!environment.id().equals(currentId)) {
                throw new BusinessException(ProjectErrorCode.ENVIRONMENT_NAME_CONFLICT);
            }
        });
    }

    private String normalizeBaseUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new BusinessException(ProjectErrorCode.INVALID_BASE_URL);
            }
            String normalized = uri.normalize().toString();
            return normalized.endsWith("/")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        } catch (URISyntaxException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_BASE_URL);
        }
    }

    private String normalizeAllowedMethods(String value) {
        if (value == null || value.isBlank()) {
            return "GET,POST";
        }
        List<String> supported = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
        List<String> methods = java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .map(method -> method.toUpperCase(Locale.ROOT))
                .filter(method -> !method.isBlank())
                .distinct()
                .toList();
        if (methods.isEmpty() || !supported.containsAll(methods)) {
            throw new BusinessException(
                    ProjectErrorCode.INVALID_EXECUTION_POLICY,
                    "允许方法仅支持 GET、POST、PUT、PATCH、DELETE"
            );
        }
        return String.join(",", methods);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
