package com.dochelper.common.security;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 匿名免登录模式下的当前用户访问器，始终返回固定匿名用户 ID（0L）。
 */
@Component
public class CurrentUserAccessor {

    public static final Long ANONYMOUS_USER_ID = 0L;

    /**
     * 获取当前操作用户 ID，开源匿名模式下始终返回 0L。
     *
     * @return 匿名用户 ID Mono
     */
    public Mono<Long> userId() {
        return Mono.just(ANONYMOUS_USER_ID);
    }
}
