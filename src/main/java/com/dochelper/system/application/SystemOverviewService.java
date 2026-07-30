package com.dochelper.system.application;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;
import com.dochelper.infrastructure.config.InfrastructureEndpointProperties;
import com.dochelper.infrastructure.config.ObjectStorageProperties;
import com.dochelper.infrastructure.redis.NamespacedRedisKeyFactory;
import com.dochelper.system.api.vo.InfrastructureOverviewResponse;
import com.dochelper.system.domain.model.SystemSetting;
import com.dochelper.system.domain.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 汇总应用与公共开发设施的隔离信息。
 */
@Service
public class SystemOverviewService {

    private static final String SCHEMA_VERSION_KEY = "application.schema.version";

    private final String applicationName;
    private final SystemSettingRepository systemSettingRepository;
    private final NamespacedRedisKeyFactory redisKeyFactory;
    private final InfrastructureEndpointProperties infrastructureProperties;
    private final ObjectStorageProperties objectStorageProperties;

    /**
     * 创建系统概览服务。
     *
     * @param applicationName 应用名称
     * @param systemSettingRepository 系统设置仓储
     * @param redisKeyFactory Redis Key 工厂
     * @param infrastructureProperties 基础设施配置
     * @param objectStorageProperties 对象存储配置
     */
    public SystemOverviewService(
            @Value("${spring.application.name}") String applicationName,
            SystemSettingRepository systemSettingRepository,
            NamespacedRedisKeyFactory redisKeyFactory,
            InfrastructureEndpointProperties infrastructureProperties,
            ObjectStorageProperties objectStorageProperties
    ) {
        this.applicationName = applicationName;
        this.systemSettingRepository = systemSettingRepository;
        this.redisKeyFactory = redisKeyFactory;
        this.infrastructureProperties = infrastructureProperties;
        this.objectStorageProperties = objectStorageProperties;
    }

    /**
     * 查询系统基础设施概览。
     *
     * @return 基础设施概览
     */
    @Transactional(readOnly = true)
    public InfrastructureOverviewResponse getOverview() {
        SystemSetting schemaVersion = systemSettingRepository.findByKey(SCHEMA_VERSION_KEY)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "数据库结构版本记录不存在"
                ));

        return new InfrastructureOverviewResponse(
                applicationName,
                schemaVersion.value(),
                redisKeyFactory.namespace(),
                infrastructureProperties.qdrantCollection(),
                objectStorageProperties.bucket()
        );
    }
}
