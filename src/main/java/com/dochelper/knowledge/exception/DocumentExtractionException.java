package com.dochelper.knowledge.exception;

/**
 * 文档内容提取异常。
 */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentExtractionException(String message) {
        super(message);
    }
}
