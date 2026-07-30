package com.dochelper.executor.api.dto;

import com.dochelper.executor.domain.ApiKeyLocation;
import com.dochelper.executor.domain.AuthenticationType;
import jakarta.validation.constraints.Size;

/**
 * 单步请求认证配置。
 */
public record AuthenticationRequest(
        AuthenticationType type,
        @Size(max = 4096) String token,
        @Size(max = 256) String username,
        @Size(max = 4096) String password,
        @Size(max = 128) String apiKeyName,
        @Size(max = 4096) String apiKeyValue,
        ApiKeyLocation apiKeyLocation
) {
}
