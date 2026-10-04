package com.dochelper.maintenance.api.vo;

import java.time.LocalDateTime;

/** 预览中的数量只覆盖本批次，批次最多五百个任务和执行。 */
public record CleanupPreviewResponse(String previewId, LocalDateTime before, LocalDateTime expiresAt,
        int tasks, int reports, int executions, String message) { }
