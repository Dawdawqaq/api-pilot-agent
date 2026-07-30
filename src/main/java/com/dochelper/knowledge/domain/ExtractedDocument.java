package com.dochelper.knowledge.domain;

/**
 * Tika 文本提取结果。
 */
public record ExtractedDocument(String title, String content, String detectedContentType) {
}
