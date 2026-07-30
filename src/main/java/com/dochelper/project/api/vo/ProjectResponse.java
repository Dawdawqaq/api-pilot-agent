package com.dochelper.project.api.vo;

import java.time.LocalDateTime;

import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.ProjectStatus;

/**
 * 被测项目响应。
 *
 * @param id 项目标识
 * @param code 项目编码
 * @param name 项目名称
 * @param description 项目说明
 * @param status 项目状态
 * @param currentImportId 当前 OpenAPI 导入版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ProjectResponse(
        Long id,
        String code,
        String name,
        String description,
        ProjectStatus status,
        Long currentImportId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProjectResponse from(ApiProject project) {
        return new ProjectResponse(
                project.id(),
                project.code(),
                project.name(),
                project.description(),
                project.status(),
                project.currentImportId(),
                project.createdAt(),
                project.updatedAt()
        );
    }
}
