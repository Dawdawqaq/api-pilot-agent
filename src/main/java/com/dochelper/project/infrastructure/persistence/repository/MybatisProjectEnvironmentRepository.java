package com.dochelper.project.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.project.domain.ProjectEnvironment;
import com.dochelper.project.domain.repository.ProjectEnvironmentRepository;
import com.dochelper.project.infrastructure.persistence.entity.ProjectEnvironmentEntity;
import com.dochelper.project.infrastructure.persistence.mapper.ProjectEnvironmentMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的项目环境仓储实现。
 */
@Repository
public class MybatisProjectEnvironmentRepository implements ProjectEnvironmentRepository {

    private final ProjectEnvironmentMapper mapper;

    public MybatisProjectEnvironmentRepository(ProjectEnvironmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ProjectEnvironment save(ProjectEnvironment environment) {
        ProjectEnvironmentEntity entity = toEntity(environment);
        mapper.insert(entity);
        return findByIdAndProjectId(entity.getId(), environment.projectId()).orElseThrow();
    }

    @Override
    public Optional<ProjectEnvironment> findByIdAndProjectId(Long id, Long projectId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ProjectEnvironmentEntity>lambdaQuery()
                        .eq(ProjectEnvironmentEntity::getId, id)
                        .eq(ProjectEnvironmentEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public Optional<ProjectEnvironment> findByName(Long projectId, String name) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ProjectEnvironmentEntity>lambdaQuery()
                        .eq(ProjectEnvironmentEntity::getProjectId, projectId)
                        .eq(ProjectEnvironmentEntity::getEnvironmentName, name)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<ProjectEnvironment> findByProjectId(Long projectId) {
        return mapper.selectList(
                Wrappers.<ProjectEnvironmentEntity>lambdaQuery()
                        .eq(ProjectEnvironmentEntity::getProjectId, projectId)
                        .orderByDesc(ProjectEnvironmentEntity::getIsDefault)
                        .orderByAsc(ProjectEnvironmentEntity::getCreatedAt)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public void update(ProjectEnvironment environment) {
        mapper.updateById(toEntity(environment));
    }

    @Override
    public void clearDefault(Long projectId, Long excludedId) {
        mapper.update(
                null,
                Wrappers.<ProjectEnvironmentEntity>lambdaUpdate()
                        .eq(ProjectEnvironmentEntity::getProjectId, projectId)
                        .ne(excludedId != null, ProjectEnvironmentEntity::getId, excludedId)
                        .set(ProjectEnvironmentEntity::getIsDefault, false)
        );
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private ProjectEnvironmentEntity toEntity(ProjectEnvironment environment) {
        ProjectEnvironmentEntity entity = new ProjectEnvironmentEntity();
        entity.setId(environment.id());
        entity.setProjectId(environment.projectId());
        entity.setEnvironmentName(environment.name());
        entity.setBaseUrl(environment.baseUrl());
        entity.setAllowedMethods(environment.allowedMethods());
        entity.setAllowPrivateNetwork(environment.allowPrivateNetwork());
        entity.setIsDefault(environment.defaultEnvironment());
        return entity;
    }

    private ProjectEnvironment toDomain(ProjectEnvironmentEntity entity) {
        return new ProjectEnvironment(
                entity.getId(),
                entity.getProjectId(),
                entity.getEnvironmentName(),
                entity.getBaseUrl(),
                entity.getAllowedMethods(),
                Boolean.TRUE.equals(entity.getAllowPrivateNetwork()),
                Boolean.TRUE.equals(entity.getIsDefault()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
