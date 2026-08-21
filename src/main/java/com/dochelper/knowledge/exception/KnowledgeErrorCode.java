package com.dochelper.knowledge.exception;

import com.dochelper.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 知识库错误码。
 */
public enum KnowledgeErrorCode implements ErrorCode {

    EMPTY_FILE("KNOWLEDGE_400_001", "文档文件不能为空", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE("KNOWLEDGE_413_001", "文档文件超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FILE_TYPE(
            "KNOWLEDGE_400_002",
            "仅支持 PDF、Markdown、TXT 和 YAML 文件",
            HttpStatus.BAD_REQUEST
    ),
    DOCUMENT_NOT_FOUND("KNOWLEDGE_404_001", "知识库文档不存在", HttpStatus.NOT_FOUND),
    DOCUMENT_NOT_RETRYABLE("KNOWLEDGE_409_001", "仅失败文档可以重试索引", HttpStatus.CONFLICT),
    DOCUMENT_NOT_INDEXED("KNOWLEDGE_409_002", "知识库文档尚未完成索引", HttpStatus.CONFLICT),
    EXTRACTION_FAILED("KNOWLEDGE_422_001", "文档文本解析失败", HttpStatus.UNPROCESSABLE_ENTITY),
    INDEXING_FAILED("KNOWLEDGE_500_001", "文档索引失败", HttpStatus.INTERNAL_SERVER_ERROR),
    EMPTY_QUERY("RETRIEVAL_400_001", "检索问题不能为空", HttpStatus.BAD_REQUEST),
    EVALUATION_CASE_CONFLICT("RETRIEVAL_409_001", "评测用例名称已存在", HttpStatus.CONFLICT),
    NO_EVALUATION_CASES("RETRIEVAL_409_002", "当前项目没有检索评测用例", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    KnowledgeErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() { return code; }

    @Override
    public String message() { return message; }

    @Override
    public HttpStatus httpStatus() { return httpStatus; }
}
