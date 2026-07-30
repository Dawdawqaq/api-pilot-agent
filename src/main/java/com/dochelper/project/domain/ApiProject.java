package com.dochelper.project.domain;

import java.time.LocalDateTime;

/**
 * 被测项目领域模型。
 *
 * @param id 项目标识
 * @param code 项目编码
 * @param name 项目名称
 * @param description 项目说明
 * @param status 项目状态
 * @param currentImportId 当前生效的 OpenAPI 导入版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ApiProject(
        Long id,
        String code,
        String name,
        String description,
        ProjectStatus status,
        Long currentImportId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
