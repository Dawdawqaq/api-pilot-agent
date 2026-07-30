package com.dochelper.knowledge.application;

import java.util.List;

import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.ChunkDraft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证章节感知切片与滑动重叠策略。
 */
class SectionAwareChunkerTest {

    private final SectionAwareChunker chunker = new SectionAwareChunker(
            new KnowledgeProperties(10 * 1024 * 1024, 500_000, 200, 40, 20, 60)
    );

    @Test
    void shouldKeepSectionTitleAndOverlapLongContent() {
        String longContent = "登录认证需要携带访问令牌".repeat(30);

        List<ChunkDraft> chunks = chunker.chunk(
                "认证指南",
                "# 登录接口\n" + longContent + "\n# 错误码\nAUTH_401 表示令牌无效"
        );

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks.get(0).sectionTitle()).isEqualTo("登录接口");
        assertThat(chunks.get(1).sectionTitle()).isEqualTo("登录接口");
        assertThat(chunks.get(chunks.size() - 1).sectionTitle()).isEqualTo("错误码");
        assertThat(chunks.get(0).content())
                .endsWith(chunks.get(1).content().substring(0, 40));
        assertThat(chunks).extracting(ChunkDraft::index)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, chunks.size()).boxed().toList()
                );
    }
}
