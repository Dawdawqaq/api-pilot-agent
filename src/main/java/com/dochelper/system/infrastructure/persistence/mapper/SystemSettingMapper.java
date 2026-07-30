package com.dochelper.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.system.infrastructure.persistence.entity.SystemSettingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统设置 MyBatis-Plus Mapper。
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSettingEntity> {
}
