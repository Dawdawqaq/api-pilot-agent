package com.dochelper.model;

import com.dochelper.compatibility.stub.StubChatModel;
import com.dochelper.model.application.LlmConfigurationService;
import com.dochelper.model.domain.LlmConfiguration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/** 每轮规划持有同一个模型快照，配置更新不会改变正在调用的供应商或审计名称。 */
@Component
public class RuntimeModelProvider {
    private final LlmConfigurationService configuration;
    private final RuntimeChatModelFactory factory;
    private Snapshot cached;

    public RuntimeModelProvider(LlmConfigurationService configuration, RuntimeChatModelFactory factory) {
        this.configuration = configuration;
        this.factory = factory;
    }

    public synchronized Snapshot snapshot() {
        var resolved = configuration.resolveActive();
        if (cached == null || !cached.configuration().equals(resolved.config())) {
            cached = new Snapshot(resolved.config(), "API".equals(resolved.config().mode())
                    ? factory.create(resolved) : new StubChatModel("本地 Stub 响应"));
        }
        return cached;
    }

    public record Snapshot(LlmConfiguration configuration, ChatModel chatModel) {
        public boolean offline() { return "OFFLINE".equals(configuration.mode()); }
        public ModelProviderProperties auditProperties() {
            return new ModelProviderProperties(configuration.baseUrl(), null, configuration.model(), null, null, null, null);
        }
    }
}
