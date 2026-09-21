package com.dochelper.agent.application;

import java.util.List;
import java.util.Optional;

import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 验证规划上下文包含实际字段结构，并且本地循环引用不会无限展开。
 */
class PlanningSchemaResolverTest {
    @Test
    void shouldCollectNestedLocalReferencesOnceAndIgnoreExternalReferences() {
        var catalog = mock(OpenApiCatalogRepository.class);
        when(catalog.findSchemaJson(1L, 2L, "Post")).thenReturn(Optional.of("""
                {"properties":{"author":{"$ref":"#/components/schemas/Author"},
                "parent":{"$ref":"#/components/schemas/Post"},
                "remote":{"$ref":"https://example.invalid/schema"}}}
                """));
        when(catalog.findSchemaJson(1L, 2L, "Author")).thenReturn(Optional.of("""
                {"properties":{"username":{"type":"string"}}}
                """));
        var endpoint = new ApiEndpoint(3L, 1L, 2L, "/posts", "GET", "posts", "帖子", null, "[]",
                false, null, "{\"$ref\":\"#/components/schemas/Post\"}", null, List.of());
        var result = new PlanningSchemaResolver(catalog, new ObjectMapper()).collect(List.of(endpoint));
        assertThat(result).hasSize(2);
        assertThat(result.get("2:#/components/schemas/Author").path("properties").has("username")).isTrue();
        verify(catalog, times(1)).findSchemaJson(1L, 2L, "Post");
        verify(catalog, times(1)).findSchemaJson(1L, 2L, "Author");
        verifyNoMoreInteractions(catalog);
    }
}
