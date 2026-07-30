package com.dochelper.project.api.dto;

import com.dochelper.project.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新被测项目请求。
 *
 * @param name 项目名称
 * @param description 项目说明
 * @param status 项目状态
 */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description,
        @NotNull ProjectStatus status
) {
}
