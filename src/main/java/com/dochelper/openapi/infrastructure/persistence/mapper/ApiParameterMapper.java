package com.dochelper.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiParameterEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 参数 Mapper。
 */
@Mapper
public interface ApiParameterMapper extends BaseMapper<ApiParameterEntity> {
}
