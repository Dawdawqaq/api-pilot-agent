package com.dochelper.infrastructure.redis;

import com.dochelper.infrastructure.config.RedisNamespaceProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * 统一生成带项目命名空间的 Redis Key，防止共享实例中的项目间冲突。
 */
@Component
public class NamespacedRedisKeyFactory {

    private final String namespace;

    /**
     * 创建 Redis Key 工厂。
     *
     * @param properties 命名空间配置
     */
    public NamespacedRedisKeyFactory(RedisNamespaceProperties properties) {
        this.namespace = properties.namespace();
    }

    /**
     * 为业务 Key 添加项目级前缀。
     *
     * @param businessKey 不含项目前缀的业务 Key
     * @return 完整 Redis Key
     */
    public String create(String businessKey) {
        Assert.hasText(businessKey, "业务 Key 不能为空");
        Assert.isTrue(!businessKey.startsWith(namespace), "业务 Key 不应重复包含项目命名空间");
        return namespace + businessKey;
    }

    /**
     * 返回当前项目命名空间。
     *
     * @return Redis Key 前缀
     */
    public String namespace() {
        return namespace;
    }
}
