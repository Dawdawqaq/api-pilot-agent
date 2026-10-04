package com.dochelper.maintenance.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 二次确认绑定服务器生成的范围和项目编码。 */
public record CleanupRequest(@NotBlank String previewId, @NotBlank String projectCode) { }
