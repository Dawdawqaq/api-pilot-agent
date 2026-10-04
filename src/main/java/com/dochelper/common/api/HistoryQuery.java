package com.dochelper.common.api;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;

/** 历史查询参数，游标始终是数据库主键，不使用容易受新增记录影响的页偏移。 */
public record HistoryQuery(int limit, Long beforeId, String query) {
    public HistoryQuery {
        query = query == null ? "" : query.trim();
        if (limit < 1 || limit > 100 || (beforeId != null && beforeId <= 0) || query.length() > 200) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT,
                    "每次读取数量须为 1—100，游标须为正整数，搜索词最多 200 字符");
        }
    }

    /** 转义 LIKE 通配符，使搜索词按字面内容匹配。 */
    public String escapedQuery() {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
