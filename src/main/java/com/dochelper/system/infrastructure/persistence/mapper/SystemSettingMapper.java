package com.dochelper.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.system.infrastructure.persistence.entity.SystemSettingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 系统设置 MyBatis-Plus Mapper。
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSettingEntity> {
    /** 使用唯一键保证首次保存和后续更新的一致性。 */
    @Insert("""
            INSERT INTO sys_setting (id, config_key, config_value, description)
            VALUES (#{id}, #{key}, #{value}, #{description})
            ON DUPLICATE KEY UPDATE config_value = #{value}, description = #{description},
                updated_at = CURRENT_TIMESTAMP(3), deleted = 0
            """)
    void upsert(@Param("id") Long id, @Param("key") String key,
                @Param("value") String value, @Param("description") String description);
}
