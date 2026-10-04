package com.dochelper.secret.application;

import java.time.Duration;
import java.util.Optional;

/**
 * 运行时敏感值存储，只向业务表暴露不可逆引用。
 */
public interface SecretStore {

    String put(String scope, String value, Duration ttl);

    /** 保存长期配置密钥，由配置更新或删除操作负责清理。 */
    String putPermanent(String scope, String value);

    Optional<String> get(String reference);

    void delete(String reference);
}
