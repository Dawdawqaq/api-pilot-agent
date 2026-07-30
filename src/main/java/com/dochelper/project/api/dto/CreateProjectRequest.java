package com.dochelper.project.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建被测项目请求。
 *
 * @param code 项目编码
 * @param name 项目名称
 * @param description 项目说明
 */
public record CreateProjectRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "项目编码必须以小写字母开头，且只能包含小写字母、数字和短横线")
        String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description
) {
}
