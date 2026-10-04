package com.dochelper.system.domain.repository;

import java.util.Optional;

import com.dochelper.system.domain.model.SystemSetting;

/**
 * 系统设置仓储契约。
 */
public interface SystemSettingRepository {

    /**
     * 按配置键查询系统设置。
     *
     * @param key 配置键
     * @return 系统设置
     */
    Optional<SystemSetting> findByKey(String key);

    /** 按唯一配置键新增或覆盖设置。 */
    void save(String key, String value, String description);
}
