package com.dochelper.executor.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.project.domain.ProjectEnvironment;
import org.springframework.stereotype.Component;

/**
 * 在发送请求前执行环境白名单、HTTP 方法和 SSRF 校验。
 */
@Component
public class TargetAccessPolicy {

    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "host",
            "content-length",
            "connection",
            "transfer-encoding",
            "upgrade",
            "proxy-authorization",
            "proxy-authenticate"
    );

    public String validateAndNormalizeMethod(
            ProjectEnvironment environment,
            ExecutionStepRequest step
    ) {
        String method = step.method().trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = Arrays.stream(environment.allowedMethods().split(","))
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowed.contains(method)) {
            throw new BusinessException(
                    ExecutionErrorCode.METHOD_NOT_ALLOWED,
                    "环境 " + environment.name() + " 不允许 " + method + " 请求"
            );
        }
        if ("DELETE".equals(method) && !Boolean.TRUE.equals(step.dangerousOperationConfirmed())) {
            throw new BusinessException(ExecutionErrorCode.DANGEROUS_CONFIRMATION_REQUIRED);
        }
        validateHeaders(step);
        return method;
    }

    public void validateTarget(ProjectEnvironment environment, URI target) {
        URI base = URI.create(environment.baseUrl());
        if (!equalsIgnoreCase(base.getScheme(), target.getScheme())
                || !equalsIgnoreCase(base.getHost(), target.getHost())
                || effectivePort(base) != effectivePort(target)) {
            throw new BusinessException(
                    ExecutionErrorCode.TARGET_BLOCKED,
                    "请求目标必须与环境 Base URL 同源"
            );
        }
        if (target.getUserInfo() != null || target.getFragment() != null) {
            throw new BusinessException(ExecutionErrorCode.TARGET_BLOCKED);
        }
        validateResolvedAddresses(target.getHost(), environment.allowPrivateNetwork());
    }

    public void validateRelativePath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (!normalized.startsWith("/")
                || normalized.startsWith("//")
                || normalized.contains("://")
                || normalized.contains("\\")
                || normalized.indexOf('\0') >= 0
                || Arrays.asList(normalized.split("/")).contains("..")) {
            throw new BusinessException(
                    ExecutionErrorCode.TARGET_BLOCKED,
                    "请求路径必须是安全的站内绝对路径"
            );
        }
    }

    private void validateHeaders(ExecutionStepRequest step) {
        if (step.headers() == null) {
            return;
        }
        step.headers().forEach((name, value) -> {
            String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()
                    || BLOCKED_HEADERS.contains(normalized)
                    || containsLineBreak(name)
                    || containsLineBreak(value)) {
                throw new BusinessException(
                        ExecutionErrorCode.TARGET_BLOCKED,
                        "请求头不允许覆盖：" + name
                );
            }
        });
    }

    private void validateResolvedAddresses(String host, boolean allowPrivateNetwork) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!allowPrivateNetwork && isPrivateAddress(address)) {
                    throw new BusinessException(
                            ExecutionErrorCode.TARGET_BLOCKED,
                            "目标域名解析到了私网、环回或链路本地地址"
                    );
                }
            }
        } catch (UnknownHostException exception) {
            throw new BusinessException(
                    ExecutionErrorCode.TARGET_BLOCKED,
                    "目标域名无法解析"
            );
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            int first = address.getAddress()[0] & 0xFF;
            return (first & 0xFE) == 0xFC;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.contains("\r") || value.contains("\n"));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
