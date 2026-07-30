package com.dochelper.project.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.project.domain.ProjectEnvironment;

/**
 * 项目环境仓储。
 */
public interface ProjectEnvironmentRepository {

    ProjectEnvironment save(ProjectEnvironment environment);

    Optional<ProjectEnvironment> findByIdAndProjectId(Long id, Long projectId);

    Optional<ProjectEnvironment> findByName(Long projectId, String name);

    List<ProjectEnvironment> findByProjectId(Long projectId);

    void update(ProjectEnvironment environment);

    void clearDefault(Long projectId, Long excludedId);

    void deleteById(Long id);
}
