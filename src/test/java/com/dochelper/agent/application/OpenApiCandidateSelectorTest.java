package com.dochelper.agent.application;

import java.util.List;

import com.dochelper.openapi.domain.ApiEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OpenAPI 候选接口按业务名词和操作形态排序。
 */
class OpenApiCandidateSelectorTest {

    private final OpenApiCandidateSelector selector = new OpenApiCandidateSelector();

    @Test
    void shouldPreferGeneralPostListOverBookmarkListForAmbiguousGoal() {
        ApiEndpoint posts = endpoint(1L, "/api/posts", "GET", "posts", "分页获取帖子");
        ApiEndpoint bookmarks = endpoint(2L, "/api/bookmarks", "GET", "bookmarks", "我的收藏列表");
        ApiEndpoint detail = endpoint(3L, "/api/posts/{id}", "GET", "detail", "获取帖子详情");

        List<OpenApiCandidateSelector.RankedEndpoint> result = selector.select(
                "查询最新帖子列表，验证接口返回 200，并检查响应中包含帖子列表数据",
                List.of(bookmarks, detail, posts),
                12
        );

        assertThat(result).extracting(candidate -> candidate.endpoint().path())
                .containsExactly("/api/posts", "/api/posts/{id}");
        assertThat(result.getFirst().score()).isGreaterThan(result.get(1).score());
        assertThat(result.getFirst().matchedTerms()).contains("帖子");
    }

    @Test
    void shouldGiveExplicitPathTheHighestPriority() {
        ApiEndpoint posts = endpoint(1L, "/api/posts", "GET", "posts", "分页获取帖子");
        ApiEndpoint bookmarks = endpoint(2L, "/api/bookmarks", "GET", "bookmarks", "我的收藏列表");

        List<OpenApiCandidateSelector.RankedEndpoint> result = selector.select(
                "只调用 GET /api/posts 并验证状态码",
                List.of(bookmarks, posts),
                12
        );

        assertThat(result.getFirst().endpoint()).isEqualTo(posts);
        assertThat(result.getFirst().reasons()).contains("目标显式包含接口路径");
    }

    @Test
    void shouldKeepDeterministicFallbackWhenGoalHasNoMeaningfulMatch() {
        ApiEndpoint users = endpoint(1L, "/api/users", "GET", "users", "搜索用户");
        ApiEndpoint posts = endpoint(2L, "/api/posts", "GET", "posts", "分页获取帖子");

        List<OpenApiCandidateSelector.RankedEndpoint> result = selector.select(
                "执行基础验证",
                List.of(users, posts),
                1
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().endpoint().path()).isEqualTo("/api/posts");
    }

    private ApiEndpoint endpoint(Long id, String path, String method, String operationId, String summary) {
        return new ApiEndpoint(
                id, 1L, 2L, path, method, operationId, summary, null,
                "[]", false, null, "{}", null, List.of()
        );
    }
}
