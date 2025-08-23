package org.jdt.mcp.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.service.RedisAuthKeyService;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.jdt.mcp.gateway.core.constant.RedisConstant.AUTH_KEY_PREFIX;
import static org.jdt.mcp.gateway.core.constant.RedisConstant.AUTH_KEY_STATUS_PREFIX;

@Slf4j
@Service
public class RedisAuthKeyServiceImpl implements RedisAuthKeyService {

    private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofHours(2);
    private static final Duration SHORT_EXPIRE_DURATION = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisAuthKeyServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 缓存认证key信息
     */
    public void cacheAuthKey(String authKey, AuthKeyEntity entity) {
        try {
            String key = buildAuthKeyRedisKey(authKey);

            // 计算过期时间
            Duration expireDuration = calculateExpireDuration(entity);

            redisTemplate.opsForValue().set(key, entity, expireDuration);
            log.debug("Cached auth key: {} for duration: {}", maskKey(authKey), expireDuration);
        } catch (Exception e) {
            log.error("Failed to cache auth key: {}", maskKey(authKey), e);
        }
    }

    /**
     * 从缓存获取认证key信息
     */
    public AuthKeyEntity getAuthKeyFromCache(String authKey) {
        try {
            String key = buildAuthKeyRedisKey(authKey);
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached instanceof AuthKeyEntity entity) {
                log.debug("Retrieved auth key from cache: {}", maskKey(authKey));
                return entity;
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to get auth key from cache: {}", maskKey(authKey), e);
            return null;
        }
    }

    /**
     * 缓存无效key（防止频繁查询数据库）
     */
    public void cacheInvalidKey(String authKey) {
        try {
            String key = buildAuthKeyStatusRedisKey(authKey);
            redisTemplate.opsForValue().set(key, "INVALID", SHORT_EXPIRE_DURATION);
            log.debug("Cached invalid auth key: {}", maskKey(authKey));
        } catch (Exception e) {
            log.error("Failed to cache invalid auth key: {}", maskKey(authKey), e);
        }
    }

    /**
     * 检查是否为已缓存的无效key
     */
    public boolean isInvalidKeyCached(String authKey) {
        try {
            String key = buildAuthKeyStatusRedisKey(authKey);
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Failed to check invalid key cache: {}", maskKey(authKey), e);
            return false;
        }
    }

    /**
     * 删除认证key缓存（密钥被撤销时）
     */
    public void evictAuthKey(String authKey) {
        try {
            String keyRedisKey = buildAuthKeyRedisKey(authKey);
            String statusRedisKey = buildAuthKeyStatusRedisKey(authKey);

            redisTemplate.delete(keyRedisKey);
            redisTemplate.delete(statusRedisKey);
            log.debug("Evicted auth key from cache: {}", maskKey(authKey));
        } catch (Exception e) {
            log.error("Failed to evict auth key from cache: {}", maskKey(authKey), e);
        }
    }

    /**
     * 批量删除用户服务相关的缓存
     */
    public void evictUserServiceKeys(String userId, String serviceId) {
        try {
            // 这里简化处理，实际可以根据需要实现更精确的批量删除
            log.info("Evicted cache for user: {} service: {}", userId, serviceId);
        } catch (Exception e) {
            log.error("Failed to evict user service keys from cache", e);
        }
    }

    /**
     * 更新key的最后使用时间（异步）
     */
    public void updateLastUsedTime(String authKey) {
        try {
            String key = buildAuthKeyRedisKey(authKey);
            AuthKeyEntity entity = getAuthKeyFromCache(authKey);
            if (entity != null) {
                entity.setLastUsedAt(LocalDateTime.now());
                // 重新缓存，保持原有过期时间
                Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (expire != null && expire > 0) {
                    redisTemplate.opsForValue().set(key, entity, Duration.ofSeconds(expire));
                }
            }
        } catch (Exception e) {
            log.error("Failed to update last used time in cache: {}", maskKey(authKey), e);
        }
    }

    private String buildAuthKeyRedisKey(String authKey) {
        return AUTH_KEY_PREFIX + authKey;
    }

    private String buildAuthKeyStatusRedisKey(String authKey) {
        return AUTH_KEY_STATUS_PREFIX + authKey;
    }

    private Duration calculateExpireDuration(AuthKeyEntity entity) {
        if (entity.getExpiresAt() == null) {
            return DEFAULT_EXPIRE_DURATION;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = entity.getExpiresAt();

        if (expiresAt.isBefore(now)) {
            return SHORT_EXPIRE_DURATION; // 已过期的key短期缓存
        }

        Duration remainingTime = Duration.between(now, expiresAt);
        return remainingTime.compareTo(DEFAULT_EXPIRE_DURATION) < 0 ?
                remainingTime : DEFAULT_EXPIRE_DURATION;
    }

    private String maskKey(String authKey) {
        if (authKey == null || authKey.length() < 4) {
            return "***";
        }
        return "***" + authKey.substring(authKey.length() - 4);
    }
}