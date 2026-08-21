package com.dochelper.secret.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.secret.infrastructure.persistence.entity.RuntimeSecretEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 加密运行时秘密 Mapper。
 */
@Mapper
public interface RuntimeSecretMapper extends BaseMapper<RuntimeSecretEntity> {
}
