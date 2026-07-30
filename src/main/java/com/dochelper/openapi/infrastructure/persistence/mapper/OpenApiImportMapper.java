package com.dochelper.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.openapi.infrastructure.persistence.entity.OpenApiImportEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * OpenAPI 导入 Mapper。
 */
@Mapper
public interface OpenApiImportMapper extends BaseMapper<OpenApiImportEntity> {

    /**
     * 查询项目当前最大修订号。
     */
    @Select("SELECT COALESCE(MAX(revision_number), 0) FROM openapi_import WHERE project_id = #{projectId}")
    int selectMaxRevision(@Param("projectId") Long projectId);
}
