package com.dochelper.project.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.project.infrastructure.persistence.entity.ProjectEnvironmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目环境 Mapper。
 */
@Mapper
public interface ProjectEnvironmentMapper extends BaseMapper<ProjectEnvironmentEntity> {
}
