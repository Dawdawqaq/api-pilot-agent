package com.dochelper.infrastructure.config;

import com.dochelper.infrastructure.storage.ObjectStorageGateway;
import io.minio.MinioClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端与启动校验配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dochelper.storage", name = "enabled", havingValue = "true")
public class ObjectStorageConfiguration {

    /**
     * 创建线程安全的 MinIO 客户端。
     *
     * @param properties 对象存储配置
     * @return MinIO 客户端
     */
    @Bean
    MinioClient minioClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * 在需要时验证项目专用 Bucket，配置错误时让应用快速失败。
     *
     * @param properties 对象存储配置
     * @param gateway 对象存储网关
     * @return 启动校验任务
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "dochelper.storage",
            name = "verify-on-startup",
            havingValue = "true",
            matchIfMissing = true
    )
    ApplicationRunner objectStorageStartupVerifier(
            ObjectStorageProperties properties,
            ObjectStorageGateway gateway
    ) {
        return arguments -> {
            if (!gateway.bucketExists()) {
                throw new IllegalStateException("MinIO Bucket 不存在: " + properties.bucket());
            }
        };
    }
}
