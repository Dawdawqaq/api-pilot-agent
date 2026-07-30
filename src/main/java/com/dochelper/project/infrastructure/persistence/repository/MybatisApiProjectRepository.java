package com.dochelper.project.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.ProjectStatus;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.infrastructure.persistence.entity.ApiProjectEntity;
import com.dochelper.project.infrastructure.persistence.mapper.ApiProjectMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的被测项目仓储实现。
 */
@Repository
public class MybatisApiProjectRepository implements ApiProjectRepository {

    private final ApiProjectMapper mapper;

    public MybatisApiProjectRepository(ApiProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ApiProject save(ApiProject project) {
        ApiProjectEntity entity = toEntity(project);
        mapper.insert(entity);
        return findById(entity.getId()).orElseThrow();
    }

    @Override
    public Optional<ApiProject> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ApiProject> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ApiProjectEntity>lambdaQuery()
                        .eq(ApiProjectEntity::getProjectCode, code)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<ApiProject> findAll() {
        return mapper.selectList(
                Wrappers.<ApiProjectEntity>lambdaQuery()
                        .orderByDesc(ApiProjectEntity::getCreatedAt)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public void update(ApiProject project) {
        mapper.updateById(toEntity(project));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void updateCurrentImport(Long id, Long importId) {
        ApiProjectEntity entity = new ApiProjectEntity();
        entity.setId(id);
        entity.setCurrentImportId(importId);
        mapper.updateById(entity);
    }

    @Override
    public void lockById(Long id) {
        mapper.lockById(id);
    }

    private ApiProjectEntity toEntity(ApiProject project) {
        ApiProjectEntity entity = new ApiProjectEntity();
        entity.setId(project.id());
        entity.setProjectCode(project.code());
        entity.setProjectName(project.name());
        entity.setDescription(project.description());
        entity.setStatus(project.status().name());
        entity.setCurrentImportId(project.currentImportId());
        return entity;
    }

    private ApiProject toDomain(ApiProjectEntity entity) {
        return new ApiProject(
                entity.getId(),
                entity.getProjectCode(),
                entity.getProjectName(),
                entity.getDescription(),
                ProjectStatus.valueOf(entity.getStatus()),
                entity.getCurrentImportId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
