package com.dochelper.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiSecuritySchemeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 安全方案 Mapper。
 */
@Mapper
public interface ApiSecuritySchemeMapper extends BaseMapper<ApiSecuritySchemeEntity> {
}
