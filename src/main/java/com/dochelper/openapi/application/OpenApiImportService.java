package com.dochelper.openapi.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.openapi.config.OpenApiImportProperties;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.ImportStatus;
import com.dochelper.openapi.domain.OpenApiImport;
import com.dochelper.openapi.domain.ParsedOpenApiDocument;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.openapi.exception.OpenApiErrorCode;
import com.dochelper.openapi.exception.OpenApiParseException;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * OpenAPI 导入、版本管理、重试和接口查询服务。
 */
@Service
public class OpenApiImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiImportService.class);

    private final OpenApiDocumentParser parser;
    private final OpenApiCatalogRepository catalogRepository;
    private final ApiProjectRepository projectRepository;
    private final OpenApiImportProperties properties;

    public OpenApiImportService(
            OpenApiDocumentParser parser,
            OpenApiCatalogRepository catalogRepository,
            ApiProjectRepository projectRepository,
            OpenApiImportProperties properties
    ) {
        this.parser = parser;
        this.catalogRepository = catalogRepository;
        this.projectRepository = projectRepository;
        this.properties = properties;
    }

    public OpenApiImport importDocument(
            Long projectId,
            String fileName,
            String contentType,
            byte[] content
    ) {
        requireProject(projectId);
        validateFile(fileName, content);
        String text = decodeUtf8(content);
        String hash = sha256(content);
        return catalogRepository.findSuccessfulByHash(projectId, hash)
                .orElseGet(() -> executeImport(
                        projectId,
                        safeFileName(fileName),
                        contentType,
                        hash,
                        text,
                        null
                ));
    }

    public OpenApiImport retryImport(Long projectId, Long importId) {
        requireProject(projectId);
        OpenApiImport failed = requireImport(projectId, importId);
        if (failed.status() != ImportStatus.FAILED) {
            throw new BusinessException(OpenApiErrorCode.IMPORT_NOT_RETRYABLE);
        }
        return catalogRepository.findSuccessfulByHash(projectId, failed.contentHash())
                .orElseGet(() -> executeImport(
                        projectId,
                        failed.fileName(),
                        failed.contentType(),
                        failed.contentHash(),
                        failed.rawContent(),
                        failed.id()
                ));
    }

    public List<OpenApiImport> listImports(Long projectId) {
        requireProject(projectId);
        return catalogRepository.findImports(projectId);
    }

    public OpenApiImport getImport(Long projectId, Long importId) {
        requireProject(projectId);
        return requireImport(projectId, importId);
    }

    public List<ApiEndpoint> listEndpoints(Long projectId, Long importId) {
        requireProject(projectId);
        if (importId != null) {
            requireImport(projectId, importId);
        }
        return catalogRepository.findEndpoints(projectId, importId);
    }

    public ApiEndpoint getEndpoint(Long projectId, Long endpointId) {
        requireProject(projectId);
        return catalogRepository.findEndpoint(projectId, endpointId)
                .orElseThrow(() -> new BusinessException(
                        OpenApiErrorCode.IMPORT_NOT_FOUND,
                        "OpenAPI 接口不存在"
                ));
    }

    private OpenApiImport executeImport(
            Long projectId,
            String fileName,
            String contentType,
            String hash,
            String content,
            Long retryOfId
    ) {
        OpenApiImport attempt = catalogRepository.createAttempt(new OpenApiImport(
                null,
                projectId,
                0,
                retryOfId,
                fileName,
                contentType,
                hash,
                content,
                null,
                null,
                null,
                ImportStatus.PROCESSING,
                null,
                0,
                0,
                0,
                null,
                null
        ));
        try {
            ParsedOpenApiDocument document = parser.parse(content);
            catalogRepository.completeSuccess(attempt.id(), projectId, document);
            return requireImport(projectId, attempt.id());
        } catch (OpenApiParseException exception) {
            String message = limitErrorMessage(exception.getMessage());
            catalogRepository.completeFailure(attempt.id(), message);
            throw new BusinessException(
                    OpenApiErrorCode.INVALID_DOCUMENT,
                    "OpenAPI 文档解析失败，导入记录 ID=" + attempt.id() + "，原因：" + message
            );
        } catch (RuntimeException exception) {
            markUnexpectedFailure(attempt.id(), exception);
            throw new BusinessException(
                    OpenApiErrorCode.IMPORT_PROCESSING_FAILED,
                    "OpenAPI 导入处理失败，导入记录 ID=" + attempt.id()
            );
        }
    }

    private void markUnexpectedFailure(Long importId, RuntimeException exception) {
        LOGGER.error("OpenAPI 导入处理异常，importId={}", importId, exception);
        try {
            catalogRepository.completeFailure(
                    importId,
                    limitErrorMessage("内部处理失败：" + exception.getClass().getSimpleName())
            );
        } catch (RuntimeException markFailureException) {
            LOGGER.error("OpenAPI 导入失败状态写入异常，importId={}", importId, markFailureException);
        }
    }

    private void validateFile(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(OpenApiErrorCode.EMPTY_FILE);
        }
        if (content.length > properties.maxFileSizeBytes()) {
            throw new BusinessException(OpenApiErrorCode.FILE_TOO_LARGE);
        }
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".json")
                && !normalized.endsWith(".yaml")
                && !normalized.endsWith(".yml")) {
            throw new BusinessException(OpenApiErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(
                    OpenApiErrorCode.INVALID_DOCUMENT,
                    "OpenAPI 文件必须使用 UTF-8 编码"
            );
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String limitErrorMessage(String message) {
        String safeMessage = message == null || message.isBlank() ? "未知解析错误" : message;
        return safeMessage.length() <= 2000 ? safeMessage : safeMessage.substring(0, 2000);
    }

    private OpenApiImport requireImport(Long projectId, Long importId) {
        return catalogRepository.findByIdAndProjectId(importId, projectId)
                .orElseThrow(() -> new BusinessException(OpenApiErrorCode.IMPORT_NOT_FOUND));
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }
}
