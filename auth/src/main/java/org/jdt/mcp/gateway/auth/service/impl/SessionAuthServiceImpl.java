package org.jdt.mcp.gateway.auth.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.auth.service.SessionAuthService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于Redis的会话认证服务实现
 */
@Slf4j
@Service
public class SessionAuthServiceImpl implements SessionAuthService {

    private final ReactiveStringRedisTemplate redisTemplate;

    // Redis key前缀
    private static final String SESSION_AUTH_PREFIX = "session:auth:";

    // 默认过期时间：2小时
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);

    public SessionAuthServiceImpl(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> storeSessionAuthKey(String sessionId, String authKey, Duration ttl) {
        if (sessionId == null || authKey == null) {
            return Mono.error(new IllegalArgumentException("SessionId and AuthKey cannot be null"));
        }

        String redisKey = buildRedisKey(sessionId);
        Duration actualTtl = ttl != null ? ttl : DEFAULT_TTL;

        return redisTemplate.opsForValue()
                .set(redisKey, authKey, actualTtl)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Stored session auth mapping: {} -> {} (TTL: {})",
                                sessionId, maskKey(authKey), actualTtl);
                    } else {
                        log.warn("Failed to store session auth mapping: {}", sessionId);
                    }
                })
                .doOnError(error -> log.error("Error storing session auth mapping: {}", sessionId, error))
                .then();
    }

    @Override
    public Mono<String> getAuthKeyBySessionId(String sessionId) {
        if (sessionId == null) {
            return Mono.empty();
        }

        String redisKey = buildRedisKey(sessionId);

        return redisTemplate.opsForValue()
                .get(redisKey)
                .doOnNext(authKey -> log.debug("Retrieved authKey for session: {} -> {}",
                        sessionId, maskKey(authKey)))
                .doOnError(error -> log.error("Error retrieving authKey for session: {}", sessionId, error));
    }

    @Override
    public Mono<String> validateSessionId(String sessionId) {
        return getAuthKeyBySessionId(sessionId)
                .filter(Objects::nonNull)
                .filter(authKey -> !authKey.trim().isEmpty())
                .doOnNext(authKey -> log.info("Session validation successful: {} -> {}",
                        sessionId, maskKey(authKey)));
    }

    @Override
    public Mono<Void> removeSessionAuthKey(String sessionId) {
        if (sessionId == null) {
            return Mono.empty();
        }

        String redisKey = buildRedisKey(sessionId);

        return redisTemplate.delete(redisKey)
                .doOnNext(deletedCount -> {
                    if (deletedCount > 0) {
                        log.debug("Removed session auth mapping: {}", sessionId);
                    } else {
                        log.debug("Session auth mapping not found for removal: {}", sessionId);
                    }
                })
                .doOnError(error -> log.error("Error removing session auth mapping: {}", sessionId, error))
                .then();
    }

    @Override
    public Mono<Boolean> extendSessionTtl(String sessionId, Duration ttl) {
        if (sessionId == null || ttl == null) {
            return Mono.just(false);
        }

        String redisKey = buildRedisKey(sessionId);

        return redisTemplate.expire(redisKey, ttl)
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Extended TTL for session: {} to {}", sessionId, ttl);
                    } else {
                        log.debug("Failed to extend TTL for session: {} (may not exist)", sessionId);
                    }
                })
                .doOnError(error -> log.error("Error extending TTL for session: {}", sessionId, error));
    }

    @Override
    public Mono<Boolean> sessionExists(String sessionId) {
        if (sessionId == null) {
            return Mono.just(false);
        }

        String redisKey = buildRedisKey(sessionId);

        return redisTemplate.hasKey(redisKey)
                .doOnNext(exists -> log.debug("Session exists check: {} -> {}", sessionId, exists))
                .doOnError(error -> log.error("Error checking session existence: {}", sessionId, error));
    }

    @Override
    public Mono<Long> getSessionTtl(String sessionId) {
        if (sessionId == null) {
            return Mono.just(-2L); // 不存在
        }

        String redisKey = buildRedisKey(sessionId);

        return redisTemplate.getExpire(redisKey)
                .map(Duration::getSeconds) // 将Duration转换为Long（秒）
                .doOnNext(ttl -> log.debug("Session TTL: {} -> {} seconds", sessionId, ttl))
                .doOnError(error -> log.error("Error getting TTL for session: {}", sessionId, error));
    }

    @Override
    public Mono<Long> cleanExpiredSessions() {
        // Redis会自动清理过期key，这里主要用于统计和监控
        String pattern = SESSION_AUTH_PREFIX + "*";

        return redisTemplate.keys(pattern)
                .count()
                .doOnNext(count -> log.debug("Current session count: {}", count))
                .doOnError(error -> log.error("Error cleaning expired sessions", error));
    }

    /**
     * 构建Redis key
     */
    private String buildRedisKey(String sessionId) {
        return SESSION_AUTH_PREFIX + sessionId;
    }

    /**
     * 脱敏显示key
     */
    private String maskKey(String authKey) {
        if (authKey == null || authKey.length() < 4) {
            return "***";
        }
        return "***" + authKey.substring(authKey.length() - 4);
    }
}