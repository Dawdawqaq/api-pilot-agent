package com.dochelper.infrastructure.redis;

import com.dochelper.infrastructure.config.RedisNamespaceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Redis Key 项目隔离规则。
 */
class NamespacedRedisKeyFactoryTest {

    private final NamespacedRedisKeyFactory keyFactory =
            new NamespacedRedisKeyFactory(new RedisNamespaceProperties("dochelper:"));

    /**
     * 验证普通业务 Key 会添加统一前缀。
     */
    @Test
    void shouldPrefixBusinessKey() {
        assertThat(keyFactory.create("agent:task:100")).isEqualTo("dochelper:agent:task:100");
    }

    /**
     * 验证重复添加命名空间会被拒绝。
     */
    @Test
    void shouldRejectDuplicatedNamespace() {
        assertThatThrownBy(() -> keyFactory.create("dochelper:agent:task:100"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
