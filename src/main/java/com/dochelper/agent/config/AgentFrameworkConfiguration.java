package com.dochelper.agent.config;

import java.util.Arrays;
import java.util.List;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.dochelper.agent.tool.AgentToolService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Agent Framework 可发现的强类型工具。
 */
@Configuration(proxyBeanMethods = false)
public class AgentFrameworkConfiguration {

    /**
     * 将六个业务工具转换为 Spring AI ToolCallback。
     *
     * @param toolService Agent 工具服务
     * @return 固定工具回调列表
     */
    @Bean
    List<ToolCallback> agentToolCallbacks(AgentToolService toolService) {
        return Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(toolService)
                .build()
                .getToolCallbacks());
    }

    /**
     * 创建 Spring AI Alibaba ReAct Agent，真实模型阶段直接复用同一组受控工具。
     *
     * @param chatModel 当前 Profile 提供的对话模型
     * @param toolCallbacks 六个强类型工具
     * @return API 测试 ReAct Agent
     */
    @Bean
    ReactAgent apiTestReactAgent(
            ChatModel chatModel,
            @Qualifier("agentToolCallbacks") List<ToolCallback> toolCallbacks
    ) {
        return ReactAgent.builder()
                .name("api_pilot_agent")
                .description("检索接口知识并执行受控 API 测试的智能体")
                .instruction("""
                        你负责把用户的 API 测试目标转换为可审计的工具调用。
                        必须先检索证据，再读取接口 Schema，所有网络请求只能调用
                        executeHttpRequest。不得猜测接口、绕过危险操作确认或输出明文密钥。
                        达到工具或步骤上限时必须终止并解释原因。
                        """)
                .model(chatModel)
                .tools(toolCallbacks)
                .saver(new MemorySaver())
                .parallelToolExecution(false)
                .toolExecutionTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }
}
