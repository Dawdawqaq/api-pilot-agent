package com.dochelper.system.domain.model;

import java.time.LocalDateTime;

/**
 * 系统设置领域模型。
 *
 * @param id 主键
 * @param key 配置键
 * @param value 配置值
 * @param description 配置说明
 * @param updatedAt 最后更新时间
 */
public record SystemSetting(
        Long id,
        String key,
        String value,
        String description,
        LocalDateTime updatedAt
) {
}
