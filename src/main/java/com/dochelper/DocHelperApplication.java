package com.dochelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DocHelper 应用启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DocHelperApplication {

    /**
     * 启动 DocHelper 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DocHelperApplication.class, args);
    }
}
