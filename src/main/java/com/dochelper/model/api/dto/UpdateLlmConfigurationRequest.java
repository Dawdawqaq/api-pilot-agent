package com.dochelper.model.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 模型配置请求，空密钥表示保留已保存的密钥。 */
public record UpdateLlmConfigurationRequest(
        @NotBlank @Pattern(regexp = "API|OFFLINE", message = "请选择 API 或 OFFLINE") String mode,
        @NotBlank @Pattern(regexp = "DEEPSEEK|DASHSCOPE|OPENAI_COMPATIBLE", message = "模型供应商无效") String provider,
        @Size(max = 512) String baseUrl,
        @Size(max = 128) String model,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 4096) String apiKey
) {
    @Override
    public String toString() {
        return "UpdateLlmConfigurationRequest[mode=" + mode + ", provider=" + provider + ", apiKey=已隐藏]";
    }
}
