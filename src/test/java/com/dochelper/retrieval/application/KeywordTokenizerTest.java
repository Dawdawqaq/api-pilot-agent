package com.dochelper.retrieval.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证接口路径、错误码与中文问题的关键词提取。
 */
class KeywordTokenizerTest {

    private final KeywordTokenizer tokenizer = new KeywordTokenizer();

    @Test
    void shouldPreserveExactTechnicalTermsAndGenerateChineseBigrams() {
        List<String> terms = tokenizer.tokenize(
                "如何调用 /auth/login 处理 AUTH_401 登录认证失败"
        );

        assertThat(terms).contains("/auth/login", "AUTH_401", "登录", "认证");
        assertThat(terms).hasSizeLessThanOrEqualTo(12);
    }
}
