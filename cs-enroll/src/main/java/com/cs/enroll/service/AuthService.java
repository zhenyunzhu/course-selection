package com.cs.enroll.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 简化版登录：发一个随机 token 存 Redis（24 小时）。
 *
 * 生产应该用 JWT（自包含 + 验签），这里为了少一个依赖简化了。
 * 但「网关校验 token → 把 studentId 透传给下游」这条链路是一样的。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_PREFIX = "token:";

    private final StringRedisTemplate redis;

    public String login(Long studentId, String name) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(TOKEN_PREFIX + token, studentId + "|" + name,
                Duration.ofHours(24));
        return token;
    }

    /** token 有效则返回 studentId，否则 null */
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String v = redis.opsForValue().get(TOKEN_PREFIX + token);
        if (v == null) {
            return null;
        }
        return Long.valueOf(v.split("\\|")[0]);
    }
}
