package com.dochelper.model.application;

import com.dochelper.model.RuntimeChatModelFactory;
import com.dochelper.model.api.dto.UpdateLlmConfigurationRequest;
import com.dochelper.model.api.vo.LlmConnectionTestResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/** 以一个极小的独立请求验证草稿配置，不携带项目资料，也不保存配置。 */
@Service
public class LlmConnectionTestService {
    private final LlmConfigurationService configuration;
    private final RuntimeChatModelFactory factory;
    public LlmConnectionTestService(LlmConfigurationService configuration, RuntimeChatModelFactory factory) {
        this.configuration = configuration;
        this.factory = factory;
    }

    public LlmConnectionTestResponse test(UpdateLlmConfigurationRequest request) {
        var draft = configuration.resolveDraft(request);
        long started = System.nanoTime();
        try {
            var response = factory.create(draft).call(new Prompt("Reply with OK.",
                    OpenAiChatOptions.builder().maxTokens(16).build()));
            boolean success = response != null && response.getResult() != null && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null && !response.getResult().getOutput().getText().isBlank();
            return new LlmConnectionTestResponse(success, success ? "连接成功，模型已返回响应" : "服务未返回有效的模型响应",
                    draft.config().model(), (System.nanoTime() - started) / 1_000_000);
        } catch (Exception exception) {
            return new LlmConnectionTestResponse(false, "连接失败，请检查服务地址、模型名称和 API Key",
                    draft.config().model(), (System.nanoTime() - started) / 1_000_000);
        }
    }
}
