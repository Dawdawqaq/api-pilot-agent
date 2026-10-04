package com.dochelper.common.api;

import com.dochelper.common.exception.BusinessException;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryQueryTest {
    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> new HistoryQuery(0, null, "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new HistoryQuery(101, null, "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new HistoryQuery(20, -1L, "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new HistoryQuery(20, null, "字".repeat(201))).isInstanceOf(BusinessException.class);
    }

    @Test
    void treatsWildcardSearchAsLiteralContent() {
        assertThat(new HistoryQuery(20, null, "  100%_\\标题  ").escapedQuery()).isEqualTo("100\\%\\_\\\\标题");
    }

    @Test
    void readsBeyondTwentyAndKeepsCursorPrecision() {
        long newest = 9223372036854775801L;
        List<Long> rows = LongStream.range(0, 21).map(offset -> newest - offset).boxed().toList();
        var page = CursorPage.from(rows, 27, 20, id -> id);
        assertThat(page.items()).hasSize(20);
        assertThat(page.total()).isEqualTo(27);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("9223372036854775782");
        assertThat(page.items()).doesNotContain(rows.get(20));
    }

    @Test
    void lastAndEmptyPagesHaveNoNextCursor() {
        assertThat(CursorPage.from(List.of(3L, 2L), 2, 20, id -> id).hasMore()).isFalse();
        assertThat(CursorPage.from(List.<Long>of(), 0, 20, id -> id).nextCursor()).isNull();
    }
}
