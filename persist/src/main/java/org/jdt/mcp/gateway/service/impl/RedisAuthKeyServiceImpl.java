package org.jdt.mcp.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.constant.RedisConstant;
import org.jdt.mcp.gateway.service.RedisAuthKeyService;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.jdt.mcp.gateway.core.constant.RedisConstant.AUTH_KEY_PREFIX;
import static org.jdt.mcp.gateway.core.constant.RedisConstant.AUTH_KEY_STATUS_PREFIX;

@Slf4j
@Service
public class RedisAuthKeyServiceImpl implements RedisAuthKeyService {

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    // 缓存过期时间：30分钟
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    // 无效key缓存时间：5分钟
    private static final Duration INVALID_KEY_TTL = Duration.ofMinutes(5);
    public RedisAuthKeyServiceImpl(ReactiveStringRedisTemplate reactiveRedisTemplate,
                                   ObjectMapper objectMapper) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = objectMapper;
    }
    @Override
    public Mono<Void> cacheAuthKey(String authKey, AuthKeyEntity entity) {
        if (authKey == null || entity == null) {
            return Mono.error(new IllegalArgumentException("AuthKey and entity cannot be null"));
        }

        String cacheKey = buildCacheKey(authKey);

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(entity))
                .onErrorMap(JsonProcessingException.class,
                        e -> new RuntimeException("Failed to serialize AuthKeyEntity", e))
                .flatMap(jsonStr -> reactiveRedisTemplate.opsForValue().set(cacheKey, jsonStr, CACHE_TTL))
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Successfully cached auth key: {}", maskKey(authKey));
                    } else {
                        log.warn("Failed to cache auth key: {}", maskKey(authKey));
                    }
                })
                .doOnError(error -> log.error("Failed to cache auth key: {}", maskKey(authKey), error))
                .then();
    }

    @Override
    public Mono<AuthKeyEntity> getAuthKeyFromCache(String authKey) {
        if (authKey == null) {
            return Mono.empty();
        }

        String cacheKey = buildCacheKey(authKey);

        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(jsonStr -> {
                    try {
                        AuthKeyEntity entity = objectMapper.readValue(jsonStr, AuthKeyEntity.class);
                        log.debug("Retrieved auth key from cache: {}", maskKey(authKey));
                        return Mono.just(entity);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached auth key: {}", maskKey(authKey), e);
                        // 删除损坏的缓存
                        return reactiveRedisTemplate.delete(cacheKey).then(Mono.empty());
                    }
                })
                .doOnError(error -> log.error("Error retrieving auth key from cache: {}", maskKey(authKey), error));
    }

    @Override
    public Mono<Void> updateLastUsedTime(String authKey) {
        if (authKey == null) {
            return Mono.empty();
        }

        String cacheKey = buildCacheKey(authKey);

        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(jsonStr -> {
                    try {
                        AuthKeyEntity entity = objectMapper.readValue(jsonStr, AuthKeyEntity.class);
                        entity.setLastUsedAt(LocalDateTime.now());

                        String updatedJsonStr = objectMapper.writeValueAsString(entity);
                        return reactiveRedisTemplate.opsForValue().set(cacheKey, updatedJsonStr, CACHE_TTL);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to update last used time in cache for key: {}", maskKey(authKey), e);
                        return Mono.just(false);
                    }
                })
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Updated last used time in cache for key: {}", maskKey(authKey));
                    }
                })
                .doOnError(error -> log.error("Error updating last used time in cache: {}", maskKey(authKey), error))
                .then();
    }

    @Override
    public Mono<Void> cacheInvalidKey(String authKey) {
        if (authKey == null) {
            return Mono.empty();
        }

        String statusKey = buildInvalidKeyStatusKey(authKey);

        return reactiveRedisTemplate.opsForValue().set(statusKey, "invalid", INVALID_KEY_TTL)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Cached invalid key: {}", maskKey(authKey));
                    }
                })
                .doOnError(error -> log.error("Error caching invalid key: {}", maskKey(authKey), error))
                .then();
    }

    @Override
    public Mono<Boolean> isInvalidKeyCached(String authKey) {
        if (authKey == null) {
            return Mono.just(false);
        }

        String statusKey = buildInvalidKeyStatusKey(authKey);

        return reactiveRedisTemplate.hasKey(statusKey)
                .doOnNext(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("Found cached invalid key: {}", maskKey(authKey));
                    }
                })
                .doOnError(error -> log.error("Error checking invalid key cache: {}", maskKey(authKey), error))
                .onErrorReturn(false);
    }

    @Override
    public Mono<Void> removeFromCache(String authKey) {
        if (authKey == null) {
            return Mono.empty();
        }

        String cacheKey = buildCacheKey(authKey);
        String statusKey = buildInvalidKeyStatusKey(authKey);

        return reactiveRedisTemplate.delete(cacheKey, statusKey)
                .doOnNext(deletedCount -> log.debug("Removed {} keys from cache for: {}",
                        deletedCount, maskKey(authKey)))
                .doOnError(error -> log.error("Error removing from cache: {}", maskKey(authKey), error))
                .then();
    }

    @Override
    public Mono<Boolean> hasKeyInCache(String authKey) {
        if (authKey == null) {
            return Mono.just(false);
        }

        String cacheKey = buildCacheKey(authKey);

        return reactiveRedisTemplate.hasKey(cacheKey)
                .doOnError(error -> log.error("Error checking key existence in cache: {}", maskKey(authKey), error))
                .onErrorReturn(false);
    }

    @Override
    public Mono<Long> getCacheKeyTTL(String authKey) {
        if (authKey == null) {
            return Mono.just(-2L);
        }

        String cacheKey = buildCacheKey(authKey);

        return reactiveRedisTemplate.getExpire(cacheKey)
                .map(Duration::getSeconds)
                .doOnNext(ttl -> log.debug("Cache TTL for key {}: {} seconds", maskKey(authKey), ttl))
                .doOnError(error -> log.error("Error getting cache TTL: {}", maskKey(authKey), error))
                .onErrorReturn(-2L);
    }

    @Override
    public Mono<Void> clearAllCache() {
        String pattern = RedisConstant.AUTH_KEY_PREFIX + "*";
        String statusPattern = RedisConstant.AUTH_KEY_STATUS_PREFIX + "*";

        return reactiveRedisTemplate.keys(pattern)
                .concatWith(reactiveRedisTemplate.keys(statusPattern))
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        log.debug("No auth cache keys to clear");
                        return Mono.just(0L);
                    }
                    String[] keyArray = keys.toArray(new String[0]);
                    return reactiveRedisTemplate.delete(keyArray);
                })
                .doOnNext(deletedCount -> log.info("Cleared {} auth cache keys", deletedCount))
                .doOnError(error -> log.error("Error clearing auth cache", error))
                .then();
    }

    @Override
    public Mono<Long> getCacheSize() {
        String pattern = RedisConstant.AUTH_KEY_PREFIX + "*";

        return reactiveRedisTemplate.keys(pattern)
                .count()
                .doOnNext(count -> log.debug("Current auth cache size: {}", count))
                .doOnError(error -> log.error("Error getting cache size", error))
                .onErrorReturn(0L);
    }

    @Override
    public Mono<Boolean> extendCacheTTL(String authKey, Duration ttl) {
        if (authKey == null || ttl == null) {
            return Mono.just(false);
        }

        String cacheKey = buildCacheKey(authKey);

        return reactiveRedisTemplate.expire(cacheKey, ttl)
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Extended cache TTL for key {} to {}", maskKey(authKey), ttl);
                    }
                })
                .doOnError(error -> log.error("Error extending cache TTL: {}", maskKey(authKey), error))
                .onErrorReturn(false);
    }

    /**
     * 构建缓存key
     */
    private String buildCacheKey(String authKey) {
        return RedisConstant.AUTH_KEY_PREFIX + authKey;
    }

    /**
     * 构建无效key状态缓存key
     */
    private String buildInvalidKeyStatusKey(String authKey) {
        return RedisConstant.AUTH_KEY_STATUS_PREFIX + authKey;
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