package com.dochelper.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiEndpointEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 接口 Mapper。
 */
@Mapper
public interface ApiEndpointMapper extends BaseMapper<ApiEndpointEntity> {
}
