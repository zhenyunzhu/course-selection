package com.cs.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 统一鉴权：在网关把 token 换成 studentId，再以请求头透传给下游。
 *
 * 这样做的价值：
 *  1. 下游服务不需要各自解析 token，鉴权逻辑只有一份
 *  2. 下游拿到的是「已认证的身份」，业务代码只关心业务
 *  3. 要换鉴权方案（JWT/SSO）只改网关一处
 *
 * 白名单：登录接口不需要校验。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of("/api/auth/login");
    private static final String TOKEN_PREFIX = "token:";
    private static final String NO_TOKEN = "__NO_TOKEN__";

    private final ReactiveStringRedisTemplate redis;

    public AuthGlobalFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少 Authorization 头");
        }
        String token = auth.substring(7);
        // 注意：不能把 switchIfEmpty 接在 chain.filter() 后面 ——
        // Mono<Void> 天然是「空」的，会误触发未登录分支。这里先用占位值收敛空值。
        return redis.opsForValue().get(TOKEN_PREFIX + token)
                .defaultIfEmpty(NO_TOKEN)
                .flatMap(value -> {
                    if (NO_TOKEN.equals(value)) {
                        return unauthorized(exchange, "登录已失效，请重新登录");
                    }
                    String studentId = value.split("\\|")[0];
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-Student-Id", studentId)
                            .build();
                    log.info("[网关] 鉴权通过 studentId={} path={}", studentId, path);
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":1010,\"message\":\"" + reason + "\",\"data\":null}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
