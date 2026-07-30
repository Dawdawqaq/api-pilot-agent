package com.dochelper.common.web;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * 为每个请求生成或透传受控的追踪标识。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdWebFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdWebFilter.class.getName() + ".requestId";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    /**
     * 设置请求追踪标识并写入响应头。
     *
     * @param exchange 当前请求交换对象
     * @param chain 过滤器链
     * @return 异步完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange);
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        return chain.filter(exchange);
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String suppliedRequestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (suppliedRequestId != null && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches()) {
            return suppliedRequestId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
