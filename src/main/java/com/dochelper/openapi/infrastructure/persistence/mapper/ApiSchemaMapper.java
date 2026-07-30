package com.dochelper.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiSchemaEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI Schema Mapper。
 */
@Mapper
public interface ApiSchemaMapper extends BaseMapper<ApiSchemaEntity> {
}
