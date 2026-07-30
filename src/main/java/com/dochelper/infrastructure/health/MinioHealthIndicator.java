package com.dochelper.infrastructure.health;

import com.dochelper.infrastructure.storage.ObjectStorageGateway;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 检查 MinIO 项目 Bucket 的可访问性。
 */
@Component("minio")
@ConditionalOnProperty(prefix = "dochelper.storage", name = "enabled", havingValue = "true")
public class MinioHealthIndicator extends AbstractHealthIndicator {

    private final ObjectStorageGateway objectStorageGateway;

    /**
     * 创建 MinIO 健康检查器。
     *
     * @param objectStorageGateway 对象存储网关
     */
    public MinioHealthIndicator(ObjectStorageGateway objectStorageGateway) {
        this.objectStorageGateway = objectStorageGateway;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (objectStorageGateway.bucketExists()) {
            builder.up().withDetail("bucket", objectStorageGateway.bucketName());
            return;
        }
        builder.down().withDetail("bucket", objectStorageGateway.bucketName());
    }
}
