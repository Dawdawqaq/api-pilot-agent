package com.dochelper.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CI 或全新环境中使用的 Testcontainers 集成测试基类。
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractContainerIntegrationTest {

    /**
     * 为子类提供独立 MySQL，避免污染公共开发数据库。
     */
    @Container
    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("dochelper")
                    .withUsername("dochelper")
                    .withPassword("dochelper-test");
}
