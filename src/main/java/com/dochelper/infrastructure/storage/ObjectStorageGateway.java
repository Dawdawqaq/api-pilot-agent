package com.dochelper.infrastructure.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.dochelper.infrastructure.config.ObjectStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 封装 DocHelper 对 MinIO Bucket 的基础访问。
 */
@Component
@ConditionalOnProperty(prefix = "dochelper.storage", name = "enabled", havingValue = "true")
public class ObjectStorageGateway {

    private final MinioClient minioClient;
    private final ObjectStorageProperties properties;

    /**
     * 创建对象存储访问网关。
     *
     * @param minioClient MinIO 客户端
     * @param properties 对象存储配置
     */
    public ObjectStorageGateway(MinioClient minioClient, ObjectStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 检查项目专用 Bucket 是否存在。
     *
     * @return Bucket 是否存在
     * @throws Exception MinIO 调用异常
     */
    public boolean bucketExists() throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.bucket())
                .build());
    }

    /**
     * 返回项目专用 Bucket 名称。
     *
     * @return Bucket 名称
     */
    public String bucketName() {
        return properties.bucket();
    }

    /**
     * 上传对象并覆盖相同对象键的旧内容。
     *
     * @param objectKey 对象键
     * @param content 文件内容
     * @param contentType 内容类型
     * @throws Exception MinIO 调用异常
     */
    public void put(String objectKey, byte[] content, String contentType) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, content.length, -1)
                    .contentType(contentType)
                    .build());
        }
    }

    /**
     * 下载对象内容。
     *
     * @param objectKey 对象键
     * @return 对象字节
     * @throws Exception MinIO 调用异常
     */
    public byte[] get(String objectKey) throws Exception {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * 删除对象。
     *
     * @param objectKey 对象键
     * @throws Exception MinIO 调用异常
     */
    public void remove(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build());
    }
}
