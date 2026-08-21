package com.dochelper.knowledge.application;

import java.io.ByteArrayInputStream;

import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.ExtractedDocument;
import com.dochelper.knowledge.exception.DocumentExtractionException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * 使用 Apache Tika 统一检测类型并提取正文。
 */
@Component
public class TikaDocumentExtractor {

    private final KnowledgeProperties properties;

    public TikaDocumentExtractor(KnowledgeProperties properties) {
        this.properties = properties;
    }

    /**
     * 提取 PDF、Markdown、YAML 或纯文本正文。
     */
    public ExtractedDocument extract(byte[] content, String fileName) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        BodyContentHandler handler = new BodyContentHandler(properties.maxExtractedCharacters());
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
            String text = normalize(handler.toString());
            if (text.isBlank()) {
                throw new DocumentExtractionException("文档未提取到可索引文本");
            }
            return new ExtractedDocument(
                    firstNonBlank(metadata.get(TikaCoreProperties.TITLE), stripExtension(fileName)),
                    text,
                    firstNonBlank(metadata.get(Metadata.CONTENT_TYPE), "application/octet-stream")
            );
        } catch (DocumentExtractionException exception) {
            throw exception;
        } catch (SAXException exception) {
            throw new DocumentExtractionException("文档正文超过最大提取字符数或格式损坏", exception);
        } catch (Exception exception) {
            throw new DocumentExtractionException("Tika 无法解析该文档", exception);
        }
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
