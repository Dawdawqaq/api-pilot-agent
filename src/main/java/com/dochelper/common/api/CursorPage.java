package com.dochelper.common.api;

import java.util.List;
import java.util.function.Function;

/** 带匹配总数的历史页，下一页游标以字符串返回，防止浏览器丢失大整数精度。 */
public record CursorPage<T>(List<T> items, long total, boolean hasMore, String nextCursor) {
    public static <T> CursorPage<T> from(List<T> rows, long total, int limit, Function<T, Long> id) {
        List<T> items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        boolean hasMore = rows.size() > limit;
        String cursor = hasMore ? String.valueOf(id.apply(items.get(items.size() - 1))) : null;
        return new CursorPage<>(items, total, hasMore, cursor);
    }
}
