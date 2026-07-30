package com.dochelper.compatibility;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.dochelper.compatibility.stub.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Agent Framework 与方法工具的兼容性。
 */
class AgentFrameworkCompatibilityTest {

    /**
     * 验证 ReactAgent 可以在 Java 21 下使用 Stub 模型和方法工具完成构建。
     */
    @Test
    void shouldBuildReactAgentWithMethodTools() {
        CalculatorTools calculatorTools = new CalculatorTools();

        ReactAgent agent = ReactAgent.builder()
                .name("compatibility_agent")
                .model(new StubChatModel("阶段 0 Stub 响应"))
                .description("用于验证依赖兼容性的智能体")
                .instruction("仅执行兼容性验证")
                .methodTools(calculatorTools)
                .saver(new MemorySaver())
                .build();

        assertThat(agent).isNotNull();
    }

    /**
     * 验证方法工具可以生成结构定义并独立执行。
     */
    @Test
    void shouldCreateAndInvokeMethodTool() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new CalculatorTools())
                .build()
                .getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("add");
        assertThat(callbacks[0].call("""
                {"left": 7, "right": 5}
                """)).contains("12");
    }
}
