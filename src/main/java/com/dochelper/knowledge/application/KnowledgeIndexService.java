package com.dochelper.knowledge.application;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;
import com.dochelper.infrastructure.config.InfrastructureEndpointProperties;
import com.dochelper.knowledge.api.dto.EmbeddingConfigurationRequest;
import com.dochelper.knowledge.api.vo.EmbeddingConfigurationResponse;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.knowledge.exception.KnowledgeErrorCode;
import com.dochelper.model.DeterministicEmbeddingModel;
import com.dochelper.model.ModelProviderProperties;
import com.dochelper.secret.application.SecretStore;
import com.dochelper.secret.config.SecretStoreProperties;
import com.dochelper.system.domain.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qdrant.client.QdrantClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/** 独立集合重建成功后才切换配置；失败时保留当前索引。 */
@Service
public class KnowledgeIndexService {
    private static final String SETTING = "llm.embedding.configuration";
    private static final String PENDING = "knowledge.index.unused.collections";
    private final ObjectProvider<VectorStore> defaultStore;
    private final ObjectProvider<EmbeddingModel> defaultModel;
    private final ObjectProvider<QdrantClient> clients;
    private final SystemSettingRepository settings;
    private final SecretStore secrets;
    private final KnowledgeRepository knowledge;
    private final EmbeddingModelFactory factory;
    private final ModelProviderProperties defaults;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final String originalCollection;
    private final boolean persistent;
    private final WebClient qdrant;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private volatile boolean rebuilding;
    private volatile Active cached;
    private volatile VectorStore delegate;
    @Value("${dochelper.knowledge.enabled:true}") private boolean enabled;

    public KnowledgeIndexService(ObjectProvider<VectorStore> defaultStore, ObjectProvider<EmbeddingModel> defaultModel,
            ObjectProvider<QdrantClient> clients, SystemSettingRepository settings, SecretStore secrets,
            KnowledgeRepository knowledge, EmbeddingModelFactory factory,
            ModelProviderProperties defaults, SecretStoreProperties secretProperties, ObjectMapper mapper,
            TransactionTemplate transactions, InfrastructureEndpointProperties infrastructure, WebClient.Builder web) {
        this.defaultStore = defaultStore; this.defaultModel = defaultModel; this.clients = clients;
        this.settings = settings; this.secrets = secrets; this.knowledge = knowledge;
        this.factory = factory; this.defaults = defaults; this.mapper = mapper; this.transactions = transactions;
        this.originalCollection = infrastructure.qdrantCollection();
        this.persistent = secretProperties.masterKey() != null && !secretProperties.masterKey().isBlank();
        this.qdrant = web.baseUrl(infrastructure.qdrantHttpUrl()).build();
    }

    /** 文档写入、删除与查询持有读锁，重建期间拒绝新操作，避免半套索引。 */
    public <T> T withIndex(Supplier<T> operation) {
        if (!lock.readLock().tryLock()) throw invalid("索引正在重建，请稍后再试");
        try { return operation.get(); } finally { lock.readLock().unlock(); }
    }

    public boolean available() { return enabled && defaultStore.getIfAvailable() != null; }
    public String activeCollection() { Active active = load(); return active == null ? originalCollection : active.collection(); }
    public Integer activeDimensions() { Active active = load(); return active == null ? null : active.dimensions(); }
    private boolean rebuildRequired() {
        return settings.findByKey("knowledge.index.restore.required").map(value -> "true".equals(value.value())).orElse(false);
    }
    public void add(List<Document> documents) { withIndex(() -> { store().add(documents); return true; }); }
    public List<Document> search(SearchRequest request) { return withIndex(() -> store().similaritySearch(request)); }
    public void delete(List<String> ids) {
        withIndex(() -> {
            store().delete(ids);
            // 原始集合保留用于旧版回退，删除文档时也移除其中的相同向量。
            if (load() != null) defaultStore.getObject().delete(ids);
            return true;
        });
    }

    public EmbeddingConfigurationResponse configuration() {
        Active active = load();
        var model = defaultModel.getIfAvailable();
        boolean development = active == null && (model == null || model instanceof DeterministicEmbeddingModel);
        String mode = active == null ? (development ? "DEVELOPMENT" : "API") : active.mode();
        var indexed = knowledge.findIndexableDocuments();
        long documents = indexed.size(), chunks = indexed.stream().mapToLong(value -> value.chunkCount()).sum();
        return new EmbeddingConfigurationResponse(available(), mode,
                active == null ? (development ? "" : defaults.effectiveEmbeddingBaseUrl()) : active.baseUrl(),
                active == null ? (development ? "deterministic-local" : defaults.embeddingModel()) : active.model(),
                active == null ? (development ? 4 : Objects.requireNonNullElse(defaults.embeddingDimensions(), 0)) : active.dimensions(),
                active == null ? (development ? null : defaults.embeddingDimensions()) : active.requestedDimensions(),
                active == null ? originalCollection : active.collection(),
                active == null ? !development : active.secretReference() != null, persistent, rebuilding, rebuildRequired(), unused().size(),
                documents, chunks, rebuildRequired() ? "备份恢复后请先重建索引，再进行文档写入和检索" : !available() ? "当前运行模式未启用知识库" : "DEVELOPMENT".equals(mode)
                        ? "开发向量只用于流程验证，不能提供一般语义检索能力" : "正在使用独立的 API 嵌入模型；检索质量仍需通过业务语料评测");
    }

    /** 只发送固定探测文本；既不保存草稿，也不发送业务文档。 */
    public Map<String, Object> test(EmbeddingConfigurationRequest request) {
        Draft draft = resolve(request);
        long start = System.nanoTime();
        try {
            int actual = probe(draft.model(), request.dimensions());
            return Map.of("success", true, "dimensions", actual, "message", "连接成功，返回 " + actual + " 维向量",
                    "durationMs", (System.nanoTime() - start) / 1_000_000);
        } catch (Exception exception) {
            return Map.of("success", false, "message", "连接失败或返回维度不一致，请检查嵌入配置",
                    "durationMs", (System.nanoTime() - start) / 1_000_000);
        }
    }

    public EmbeddingConfigurationResponse rebuild(EmbeddingConfigurationRequest request) {
        if (!available()) throw new BusinessException(KnowledgeErrorCode.FEATURE_DISABLED);
        if (!lock.writeLock().tryLock()) throw invalid("知识库正在操作或重建，请稍后再试");
        String collection = null;
        boolean activated = false;
        try {
            rebuilding = true;
            Draft draft = resolve(request);
            if ("API".equals(request.mode()) && !request.allowDocumentTransfer()) {
                throw invalid("重建会将所有项目已索引的业务切片发送给所选嵌入服务，请明确允许此操作");
            }
            if ("API".equals(request.mode()) && !persistent) throw invalid("请先配置稳定的 SECRET_STORE_MASTER_KEY");
            int dimensions = probe(draft.model(), request.dimensions());
            collection = originalCollection + "_g_" + UUID.randomUUID().toString().replace("-", "");
            registerUnused(collection);
            var next = createStore(collection, fixedDimensions(draft.model(), dimensions), true);
            List<Document> batch = new ArrayList<>(16);
            var indexed = knowledge.findIndexableDocuments();
            long chunkCount = 0;
            for (var document : indexed) {
                for (var chunk : knowledge.findChunks(document.id())) {
                    chunkCount++;
                    String section = chunk.sectionTitle() == null ? document.fileName() : chunk.sectionTitle();
                    batch.add(Document.builder().id(chunk.vectorId()).text(section + "\n" + chunk.content())
                            .metadata(Map.of("project_id", document.projectId().toString(), "document_id", document.id(),
                                    "chunk_id", chunk.id(), "chunk_index", chunk.chunkIndex(),
                                    "source_name", document.fileName(), "section", section)).build());
                    if (batch.size() == 16) { next.add(List.copyOf(batch)); batch.clear(); }
                }
            }
            if (!batch.isEmpty()) next.add(batch);
            Active previous = load();
            String target = collection;
            Active saved = transactions.execute(status -> {
                String reference = "API".equals(request.mode()) ? secrets.putPermanent("llm:global:embedding", draft.key()) : null;
                Active value = new Active(request.mode(), draft.baseUrl(), draft.modelName(), dimensions,
                        request.dimensions(), target, reference);
                try { settings.save(SETTING, mapper.writeValueAsString(value), "应用级嵌入模型及生效索引（密钥引用）"); }
                catch (Exception exception) { throw new IllegalStateException("嵌入配置保存失败", exception); }
                settings.save("knowledge.index.restore.required", "false", "知识库索引已与恢复后的文档同步");
                if (previous != null) registerUnused(previous.collection());
                unregisterUnused(target);
                if (previous != null && previous.secretReference() != null) secrets.delete(previous.secretReference());
                return value;
            });
            cached = saved; delegate = next; activated = true;
            if (previous != null) removeGeneration(previous.collection());
            rebuilding = false;
            return new EmbeddingConfigurationResponse(true, saved.mode(), saved.baseUrl(), saved.model(),
                    saved.dimensions(), saved.requestedDimensions(), saved.collection(), saved.secretReference() != null, persistent, false,
                    false, unused().size(), indexed.size(), chunkCount, "DEVELOPMENT".equals(saved.mode())
                            ? "开发向量只用于流程验证，不能提供一般语义检索能力" : "正在使用独立的 API 嵌入模型；检索质量仍需通过业务语料评测");
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw invalid(activated ? "新索引已启用，但收尾检查未完成，请刷新当前配置"
                : "重建未完成，原配置和索引继续生效；请检查嵌入服务和 Qdrant"); }
        finally {
            if (!activated && collection != null) removeGeneration(collection);
            rebuilding = false;
            lock.writeLock().unlock();
        }
    }

    private synchronized VectorStore store() {
        if (rebuildRequired()) throw invalid("备份已恢复，请先在知识库配置页重建索引");
        Active active = load();
        if (active == null) return defaultStore.getObject();
        if (!active.equals(cached) || delegate == null) {
            String key = active.secretReference() == null ? null : secrets.get(active.secretReference())
                    .orElseThrow(() -> invalid("已保存的嵌入密钥不可用，请重新配置"));
            delegate = createStore(active.collection(), fixedDimensions(factory.create(active.mode(), active.baseUrl(), active.model(),
                    active.requestedDimensions(), key), active.dimensions()), false);
            cached = active;
        }
        return delegate;
    }

    VectorStore createStore(String collection, EmbeddingModel model, boolean initialize) {
        var store = QdrantVectorStore.builder(clients.getObject(), model).collectionName(collection)
                .initializeSchema(initialize).build();
        try { store.afterPropertiesSet(); return store; }
        catch (Exception exception) { throw invalid("无法准备知识库索引集合"); }
    }

    /** 输出维度与供应商的可选 dimensions 参数分开保存，兼容不接受该参数的供应商。 */
    private EmbeddingModel fixedDimensions(EmbeddingModel source, int dimensions) {
        return new EmbeddingModel() {
            @Override public int dimensions() { return dimensions; }
            @Override public float[] embed(Document document) {
                float[] vector = source.embed(document);
                if (vector.length != dimensions) throw invalid("供应商输出维度发生变化，请重新重建索引");
                return vector;
            }
            @Override public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                var response = source.call(request);
                for (var result : response.getResults()) {
                    if (result.getOutput().length != dimensions) throw invalid("供应商输出维度发生变化，请重新重建索引");
                }
                return response;
            }
        };
    }

    private int probe(EmbeddingModel model, Integer expected) {
        float[] vector = model.embed("ApiPilot 嵌入连接检查");
        if (vector == null || vector.length < 1 || vector.length > 8192
                || (expected != null && vector.length != expected)) throw invalid("嵌入返回维度与配置不一致");
        for (float value : vector) if (!Float.isFinite(value)) throw invalid("嵌入服务返回了无效向量");
        return vector.length;
    }

    private Draft resolve(EmbeddingConfigurationRequest request) {
        if ("DEVELOPMENT".equals(request.mode())) return new Draft("", "deterministic-local", null,
                factory.create("DEVELOPMENT", "", "deterministic-local", null, null));
        if (!"API".equals(request.mode())) throw invalid("请选择 API 嵌入或开发向量");
        String url = request.baseUrl() == null ? "" : request.baseUrl().trim().replaceAll("/+$", "");
        String name = request.model() == null ? "" : request.model().trim();
        try {
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || url.endsWith("/embeddings") || name.isEmpty()) throw invalid("请填写有效的基础地址和嵌入模型名称");
        } catch (IllegalArgumentException exception) { throw invalid("嵌入服务基础地址不合法"); }
        String key = request.apiKey() == null ? "" : request.apiKey().trim();
        if (key.isEmpty()) {
            Active previous = load();
            if (previous == null || !url.equals(previous.baseUrl()) || previous.secretReference() == null)
                throw invalid("请填写独立的嵌入 API Key；更换服务地址后需要重新填写");
            try { key = secrets.get(previous.secretReference()).orElseThrow(() -> invalid("嵌入密钥已失效")); }
            catch (BusinessException exception) { throw exception; }
            catch (Exception exception) { throw invalid("嵌入密钥无法解密，请检查主密钥"); }
        }
        if (key.chars().anyMatch(Character::isISOControl)) throw invalid("API Key 不能包含控制字符");
        return new Draft(url, name, key, factory.create("API", url, name, request.dimensions(), key));
    }

    private Active load() {
        return settings.findByKey(SETTING).map(setting -> {
            try { return mapper.readValue(setting.value(), Active.class); }
            catch (Exception exception) { throw new IllegalStateException("嵌入配置无法读取", exception); }
        }).orElse(null);
    }

    void removeGeneration(String collection) {
        if (!isGeneration(collection) || collection.equals(activeCollection())) return;
        try { qdrant.delete().uri("/collections/{collection}", collection).retrieve().toBodilessEntity()
                .timeout(Duration.ofSeconds(5)).block(); unregisterUnused(collection); }
        catch (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound ignored) { unregisterUnused(collection); }
        catch (Exception ignored) {
            // 删除失败只保留旧集合，不回滚已经生效的新索引；日志仅记录本项目集合名。
            org.slf4j.LoggerFactory.getLogger(KnowledgeIndexService.class).warn("旧向量集合暂未清理：{}", collection);
        }
    }
    /** 只清理明确登记且未生效的本项目集合，不扫描或删除其他命名空间。 */
    public EmbeddingConfigurationResponse cleanUnused() {
        if (!available()) throw new BusinessException(KnowledgeErrorCode.FEATURE_DISABLED);
        if (!lock.writeLock().tryLock()) throw invalid("知识库正在操作，请稍后再清理未使用索引");
        try { for (String collection : unused()) removeGeneration(collection); return configuration(); }
        finally { lock.writeLock().unlock(); }
    }

    private boolean isGeneration(String collection) {
        return collection != null && collection.matches(java.util.regex.Pattern.quote(originalCollection + "_g_") + "[a-f0-9]{32}");
    }
    private List<String> unused() {
        return settings.findByKey(PENDING).map(setting -> {
            try { return mapper.readValue(setting.value(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { }); }
            catch (Exception exception) { throw new IllegalStateException("索引清理登记无法读取", exception); }
        }).orElse(List.of());
    }
    private void registerUnused(String collection) {
        if (!isGeneration(collection)) return;
        var values = new ArrayList<>(unused());
        if (!values.contains(collection)) values.add(collection);
        saveUnused(values);
    }
    private void unregisterUnused(String collection) { saveUnused(unused().stream().filter(value -> !value.equals(collection)).toList()); }
    private void saveUnused(List<String> values) {
        try { settings.save(PENDING, mapper.writeValueAsString(values), "未启用或已替换的 DocHelper 索引集合"); }
        catch (Exception exception) { throw new IllegalStateException("索引清理登记保存失败", exception); }
    }
    private static BusinessException invalid(String message) { return new BusinessException(CommonErrorCode.INVALID_ARGUMENT, message); }
    private record Draft(String baseUrl, String modelName, String key, EmbeddingModel model) {
        @Override public String toString() { return "Draft[密钥已隐藏]"; }
    }
    public record Active(String mode, String baseUrl, String model, int dimensions, Integer requestedDimensions,
            String collection, String secretReference) { }
}
