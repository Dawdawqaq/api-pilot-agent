package com.dochelper.model;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.agent.exception.AgentErrorCode;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** 对话模型统一入口，避免应用启动阶段查询配置数据库。 */
@Component
public class RuntimeChatModel implements ChatModel {
    private final RuntimeModelProvider provider;
    public RuntimeChatModel(RuntimeModelProvider provider) { this.provider = provider; }

    @Override public ChatResponse call(Prompt prompt) { return provider.snapshot().chatModel().call(prompt); }
    @Override public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> provider.snapshot().chatModel().stream(prompt)).timeout(java.time.Duration.ofSeconds(30))
                .onErrorMap(error -> new BusinessException(AgentErrorCode.PLANNING_FAILED, "模型流式调用失败，请检查 LLM API 配置"));
    }
    @Override public ChatOptions getDefaultOptions() { return ToolCallingChatOptions.builder().build(); }
}
