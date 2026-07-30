package com.dochelper.system.infrastructure.persistence.repository;

import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.system.domain.model.SystemSetting;
import com.dochelper.system.domain.repository.SystemSettingRepository;
import com.dochelper.system.infrastructure.persistence.entity.SystemSettingEntity;
import com.dochelper.system.infrastructure.persistence.mapper.SystemSettingMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的系统设置仓储实现。
 */
@Repository
public class MybatisSystemSettingRepository implements SystemSettingRepository {

    private final SystemSettingMapper systemSettingMapper;

    /**
     * 创建系统设置仓储。
     *
     * @param systemSettingMapper 系统设置 Mapper
     */
    public MybatisSystemSettingRepository(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
    }

    @Override
    public Optional<SystemSetting> findByKey(String key) {
        SystemSettingEntity entity = systemSettingMapper.selectOne(
                Wrappers.<SystemSettingEntity>lambdaQuery()
                        .eq(SystemSettingEntity::getConfigKey, key)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    private SystemSetting toDomain(SystemSettingEntity entity) {
        return new SystemSetting(
                entity.getId(),
                entity.getConfigKey(),
                entity.getConfigValue(),
                entity.getDescription(),
                entity.getUpdatedAt()
        );
    }
}
