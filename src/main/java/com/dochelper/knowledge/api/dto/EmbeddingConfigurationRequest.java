package com.dochelper.knowledge.api.dto;

import jakarta.validation.constraints.*;

/** 嵌入配置草稿，密钥仅供本次连接或重建使用。 */
public record EmbeddingConfigurationRequest(@NotBlank String mode, @Size(max = 512) String baseUrl,
        @Size(max = 200) String model, @Min(1) @Max(8192) Integer dimensions,
        @Size(max = 4096) String apiKey, boolean allowDocumentTransfer) {
    @Override public String toString() { return "EmbeddingConfigurationRequest[mode=" + mode + ", apiKey=已隐藏]"; }
}
