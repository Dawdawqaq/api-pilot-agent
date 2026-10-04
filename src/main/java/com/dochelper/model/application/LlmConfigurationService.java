package com.dochelper.model.application;

import java.net.URI;
import java.util.Objects;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;
import com.dochelper.model.ModelProviderProperties;
import com.dochelper.model.api.dto.UpdateLlmConfigurationRequest;
import com.dochelper.model.api.vo.LlmConfigurationResponse;
import com.dochelper.model.domain.LlmConfiguration;
import com.dochelper.secret.application.SecretStore;
import com.dochelper.secret.config.SecretStoreProperties;
import com.dochelper.system.domain.repository.SystemSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 管理可热更新的对话模型配置，并将明文密钥隔离在密钥存储中。 */
@Service
public class LlmConfigurationService {
    private static final String SETTING_KEY = "llm.chat.configuration";
    private final SystemSettingRepository repository;
    private final SecretStore secrets;
    private final ModelProviderProperties defaults;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final boolean environmentModelEnabled;
    private final boolean persistentStorageReady;

    public LlmConfigurationService(SystemSettingRepository repository, SecretStore secrets,
            ModelProviderProperties defaults, SecretStoreProperties secretProperties,
            ObjectMapper mapper, TransactionTemplate transactions, Environment environment) {
        this.repository = repository;
        this.secrets = secrets;
        this.defaults = defaults;
        this.mapper = mapper;
        this.transactions = transactions;
        this.environmentModelEnabled = !environment.acceptsProfiles(Profiles.of("stub"))
                && hasText(defaults.apiKey());
        this.persistentStorageReady = hasText(secretProperties.masterKey());
    }

    public synchronized LlmConfigurationResponse getConfiguration() {
        LoadedConfiguration loaded = load();
        LlmConfiguration config = loaded.config();
        return new LlmConfigurationResponse(config.mode(), config.provider(), config.baseUrl(), config.model(),
                "API".equals(config.mode()) ? config.model() : "local-stub",
                config.secretReference() != null || (!loaded.saved() && environmentModelEnabled),
                loaded.saved() ? "SAVED" : (environmentModelEnabled ? "ENVIRONMENT" : "DEFAULT"),
                persistentStorageReady);
    }

    /** 配置和新旧密钥在同一事务内更换，失败时不会遗留半份配置。 */
    public synchronized LlmConfigurationResponse save(UpdateLlmConfigurationRequest request) {
        transactions.executeWithoutResult(status -> {
            LoadedConfiguration previous = load();
            LlmConfiguration proposed = validate(request, previous);
            String reference = previous.config().secretReference();
            String newKey = hasText(request.apiKey()) ? request.apiKey().trim() : null;
            if (newKey == null && reference == null && !previous.saved() && environmentModelEnabled) {
                newKey = defaults.apiKey();
            }
            if (newKey != null) {
                if (!persistentStorageReady) {
                    throw invalid("服务器尚未配置稳定的密钥存储主密钥，请先设置 SECRET_STORE_MASTER_KEY");
                }
                reference = secrets.putPermanent("llm:global:chat", newKey);
            }
            LlmConfiguration saved = new LlmConfiguration(proposed.mode(), proposed.provider(), proposed.baseUrl(),
                    proposed.model(), reference);
            try {
                repository.save(SETTING_KEY, mapper.writeValueAsString(saved), "应用级对话模型配置（密钥引用）");
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("模型配置序列化失败", exception);
            }
            if (previous.config().secretReference() != null && !Objects.equals(previous.config().secretReference(), reference)) {
                secrets.delete(previous.config().secretReference());
            }
        });
        return getConfiguration();
    }

    public synchronized ResolvedConfiguration resolveActive() {
        LoadedConfiguration loaded = load();
        return new ResolvedConfiguration(loaded.config(), "API".equals(loaded.config().mode()) ? keyFor(loaded) : null);
    }

    /** 使用表单草稿测试连接，不修改已保存的配置。 */
    public synchronized ResolvedConfiguration resolveDraft(UpdateLlmConfigurationRequest request) {
        LoadedConfiguration previous = load();
        LlmConfiguration proposed = validate(request, previous);
        if (!"API".equals(proposed.mode())) {
            throw invalid("请切换到 API 模型后再测试连接");
        }
        String key = hasText(request.apiKey()) ? request.apiKey().trim() : keyFor(previous);
        return new ResolvedConfiguration(proposed, key);
    }

    private LoadedConfiguration load() {
        return repository.findByKey(SETTING_KEY).map(setting -> {
            try {
                return new LoadedConfiguration(mapper.readValue(setting.value(), LlmConfiguration.class), true);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("已保存的模型配置无法读取", exception);
            }
        }).orElseGet(() -> new LoadedConfiguration(new LlmConfiguration(
                environmentModelEnabled ? "API" : "OFFLINE", providerFor(defaults.chatModel()),
                hasText(defaults.baseUrl()) ? defaults.baseUrl() : "https://api.deepseek.com",
                hasText(defaults.chatModel()) && !"local-stub".equals(defaults.chatModel()) ? defaults.chatModel() : "",
                null), false));
    }

    private LlmConfiguration validate(UpdateLlmConfigurationRequest request, LoadedConfiguration previous) {
        if (!"API".equals(request.mode()) && !"OFFLINE".equals(request.mode())) {
            throw invalid("请选择模型工作模式");
        }
        if (!java.util.Set.of("DEEPSEEK", "DASHSCOPE", "OPENAI_COMPATIBLE").contains(request.provider())) {
            throw invalid("请选择有效的模型供应商");
        }
        String baseUrl = request.baseUrl() == null ? "" : request.baseUrl().trim().replaceAll("/+$", "");
        String model = request.model() == null ? "" : request.model().trim();
        if (!baseUrl.isEmpty()) {
            try {
                URI uri = URI.create(baseUrl);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                        || uri.getFragment() != null || baseUrl.endsWith("/chat/completions")) {
                    throw invalid("请填写有效的 HTTP/HTTPS 服务基础地址，不含密钥或 /chat/completions");
                }
            } catch (IllegalArgumentException exception) {
                throw invalid("模型服务地址格式不正确");
            }
        }
        boolean priorKey = previous.config().secretReference() != null || (!previous.saved() && environmentModelEnabled);
        if (priorKey && !hasText(request.apiKey()) && (!Objects.equals(baseUrl, previous.config().baseUrl())
                || !Objects.equals(request.provider(), previous.config().provider()))) {
            throw invalid("更换服务地址或供应商后，请重新填写 API Key");
        }
        if ("API".equals(request.mode()) && (baseUrl.isEmpty() || model.isEmpty())) {
            throw invalid("请填写模型名称和服务地址");
        }
        if ("API".equals(request.mode()) && !hasText(request.apiKey()) && !priorKey) {
            throw invalid("请填写 API Key");
        }
        if (hasText(request.apiKey()) && request.apiKey().chars().anyMatch(Character::isISOControl)) {
            throw invalid("API Key 不能包含换行或控制字符");
        }
        return new LlmConfiguration(request.mode(), request.provider(), baseUrl, model, previous.config().secretReference());
    }

    private String keyFor(LoadedConfiguration loaded) {
        try {
            if (loaded.config().secretReference() != null) {
                return secrets.get(loaded.config().secretReference()).orElseThrow(() -> invalid("已保存的 API Key 不可用，请重新填写"));
            }
            if (!loaded.saved() && environmentModelEnabled) return defaults.apiKey();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("已保存的 API Key 无法解密，请检查服务器主密钥或重新填写");
        }
        throw invalid("请先配置 API Key");
    }

    public static String providerFor(String model) {
        String name = model == null ? "" : model.toLowerCase(java.util.Locale.ROOT);
        return name.contains("deepseek") ? "DEEPSEEK" : (name.contains("qwen") ? "DASHSCOPE" : "OPENAI_COMPATIBLE");
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static BusinessException invalid(String message) { return new BusinessException(CommonErrorCode.INVALID_ARGUMENT, message); }
    private record LoadedConfiguration(LlmConfiguration config, boolean saved) { }

    /** 仅在调用期间使用明文，避免默认日志字符串输出密钥。 */
    public record ResolvedConfiguration(LlmConfiguration config, String apiKey) {
        @Override public String toString() { return "ResolvedConfiguration[config=" + config + ", apiKey=已隐藏]"; }
    }
}
