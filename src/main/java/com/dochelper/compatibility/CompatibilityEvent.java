package com.dochelper.compatibility;

import java.time.Instant;

/**
 * 流式兼容性事件。
 *
 * @param sequence 事件序号
 * @param type 事件类型
 * @param content 事件内容
 * @param occurredAt 发生时间
 */
public record CompatibilityEvent(
        int sequence,
        String type,
        String content,
        Instant occurredAt
) {
}
