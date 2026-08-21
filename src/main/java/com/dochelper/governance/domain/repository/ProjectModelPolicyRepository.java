package com.dochelper.governance.domain.repository;

import java.util.Optional;

import com.dochelper.governance.domain.ProjectModelPolicy;

/**
 * 项目模型策略仓储。
 */
public interface ProjectModelPolicyRepository {
    Optional<ProjectModelPolicy> findByProjectId(Long projectId);

    ProjectModelPolicy save(ProjectModelPolicy policy);
}
