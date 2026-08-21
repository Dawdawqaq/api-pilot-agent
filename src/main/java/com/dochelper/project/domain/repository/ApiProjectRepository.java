package com.dochelper.project.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.project.domain.ApiProject;

/**
 * 被测项目仓储。
 */
public interface ApiProjectRepository {

    ApiProject save(ApiProject project);

    Optional<ApiProject> findById(Long id);

    Optional<ApiProject> findByCode(String code);

    List<ApiProject> findAll();

    List<ApiProject> findByIds(List<Long> ids);

    void update(ApiProject project);

    void deleteById(Long id);

    void updateCurrentImport(Long id, Long importId);

    void lockById(Long id);
}
