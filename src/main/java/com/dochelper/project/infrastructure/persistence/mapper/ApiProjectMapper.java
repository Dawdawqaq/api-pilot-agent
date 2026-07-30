package com.dochelper.project.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.project.infrastructure.persistence.entity.ApiProjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 被测项目 Mapper。
 */
@Mapper
public interface ApiProjectMapper extends BaseMapper<ApiProjectEntity> {

    /**
     * 锁定项目行，串行分配 OpenAPI 修订号。
     *
     * @param id 项目标识
     * @return 项目标识
     */
    @Select("SELECT id FROM api_project WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
