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

    /** 回收站项目仍占用稳定业务编码，防止新建项目时因隐藏记录产生数据库冲突。 */
    @Select("SELECT * FROM api_project WHERE project_code = #{code} LIMIT 1")
    ApiProjectEntity findByCodeIncludingRecycled(@Param("code") String code);
}
