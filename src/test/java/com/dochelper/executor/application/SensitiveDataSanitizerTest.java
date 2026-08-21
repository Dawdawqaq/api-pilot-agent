package com.dochelper.executor.application;

import java.time.Duration;

import com.dochelper.executor.config.ExecutorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证常见凭据与个人信息不会进入审计文本。
 */
class SensitiveDataSanitizerTest {

    private final SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(
            new ExecutorProperties(
                    Duration.ofSeconds(1), Duration.ofSeconds(2), 1024 * 1024,
                    512 * 1024, 10, 2, Duration.ofMillis(10),
                    "(?i).*(authorization|token|password|secret|credential|session|cookie|api[-_]?key|email|phone).*"
            )
    );

    @Test
    void shouldMaskJwtEmailPhoneAndSession() {
        String value = "session=abc123 user=a@example.com phone=13800138000 "
                + "jwt=eyJhbGciOiJIUzI1NiJ9.abcdefghijklmnop.signature123";

        assertThat(sanitizer.sanitizeText(value))
                .doesNotContain("abc123", "a@example.com", "13800138000", "eyJhbGci")
                .contains("******");
    }
}
