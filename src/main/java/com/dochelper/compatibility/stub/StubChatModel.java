package com.dochelper.compatibility.stub;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import reactor.core.publisher.Flux;

/**
 * 用于离线开发的确定性对话模型，不访问外部模型服务。
 */
public final class StubChatModel implements ChatModel {

    private final String responseText;

    /**
     * 创建固定响应模型。
     *
     * @param responseText 固定响应内容
     */
    public StubChatModel(String responseText) {
        this.responseText = responseText;
    }

    /**
     * 返回固定的对话响应。
     *
     * @param prompt 对话提示词
     * @return 固定响应
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(responseText))));
    }

    /**
     * 返回支持工具调用的默认选项，保持与 Agent Framework 的选项合并逻辑兼容。
     *
     * @return 工具调用对话选项
     */
    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    /**
     * 以单个数据块返回固定的流式响应。
     *
     * @param prompt 对话提示词
     * @return 固定流式响应
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
