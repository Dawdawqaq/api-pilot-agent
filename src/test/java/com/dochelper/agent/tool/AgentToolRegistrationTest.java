package com.dochelper.agent.tool;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证阶段 5 固定注册六个强类型 Agent 工具。
 */
class AgentToolRegistrationTest {

    @Test
    void shouldRegisterSixStronglyTypedTools() {
        AgentToolService service = new AgentToolService(null, null, null, null, null);
        String[] names = Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(service)
                        .build()
                        .getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .sorted()
                .toArray(String[]::new);

        assertThat(names).containsExactly(
                "executeHttpRequest",
                "extractResponseValue",
                "generateTestReport",
                "getApiSchema",
                "searchApiDocument",
                "validateResponse"
        );
    }
}
