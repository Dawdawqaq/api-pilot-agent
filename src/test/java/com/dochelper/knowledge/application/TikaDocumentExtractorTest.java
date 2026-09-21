package com.dochelper.knowledge.application;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.ExtractedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Tika 对文本、YAML 和 PDF 的统一解析能力。
 */
class TikaDocumentExtractorTest {

    private final TikaDocumentExtractor extractor = new TikaDocumentExtractor(
            new KnowledgeProperties(10 * 1024 * 1024, 500_000, 800, 120, 20, 60)
    );

    @Test
    void shouldExtractMarkdownContent() {
        ExtractedDocument document = extractor.extract(
                "# 登录认证\n调用 /auth/login 获取令牌".getBytes(StandardCharsets.UTF_8),
                "authentication.md"
        );

        assertThat(document.title()).isEqualTo("authentication");
        assertThat(document.content()).contains("登录认证", "/auth/login");
        assertThat(document.detectedContentType()).contains("text");
    }

    @Test
    void shouldExtractYamlContent() {
        ExtractedDocument document = extractor.extract(
                """
                service: sample-api
                rules:
                  - health_ready_should_return_200
                  - login_requires_tenant_code
                """.getBytes(StandardCharsets.UTF_8),
                "business-rules.yaml"
        );

        assertThat(document.title()).isEqualTo("business-rules");
        assertThat(document.content())
                .contains("sample-api", "health_ready_should_return_200");
    }

    @Test
    void shouldExtractPdfContent() throws Exception {
        byte[] content;
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Login API authentication guide");
                stream.endText();
            }
            pdf.save(output);
            content = output.toByteArray();
        }

        ExtractedDocument document = extractor.extract(content, "login-guide.pdf");

        assertThat(document.content()).contains("Login API authentication guide");
        assertThat(document.detectedContentType()).isEqualTo("application/pdf");
    }
}
