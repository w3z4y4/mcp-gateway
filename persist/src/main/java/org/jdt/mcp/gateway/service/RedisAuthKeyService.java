package org.jdt.mcp.gateway.service;

import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis认证Key缓存服务接口
 */
public interface RedisAuthKeyService {

    /**
     * 缓存认证key
     * @param authKey 认证key
     * @param entity 认证key实体
     * @return Mono<Void>
     */
    Mono<Void> cacheAuthKey(String authKey, AuthKeyEntity entity);

    /**
     * 从缓存获取认证key
     * @param authKey 认证key
     * @return 认证key实体，不存在返回empty
     */
    Mono<AuthKeyEntity> getAuthKeyFromCache(String authKey);

    /**
     * 更新最后使用时间
     * @param authKey 认证key
     * @return Mono<Void>
     */
    Mono<Void> updateLastUsedTime(String authKey);

    /**
     * 缓存无效key
     * @param authKey 认证key
     * @return Mono<Void>
     */
    Mono<Void> cacheInvalidKey(String authKey);

    /**
     * 检查是否是缓存的无效key
     * @param authKey 认证key
     * @return 是否是无效key
     */
    Mono<Boolean> isInvalidKeyCached(String authKey);

    /**
     * 从缓存中移除key
     * @param authKey 认证key
     * @return Mono<Void>
     */
    Mono<Void> removeFromCache(String authKey);

    /**
     * 检查key是否在缓存中
     * @param authKey 认证key
     * @return 是否存在
     */
    Mono<Boolean> hasKeyInCache(String authKey);

    /**
     * 获取缓存key的TTL
     * @param authKey 认证key
     * @return TTL秒数，-1表示永不过期，-2表示不存在
     */
    Mono<Long> getCacheKeyTTL(String authKey);

    /**
     * 清空所有认证缓存
     * @return Mono<Void>
     */
    Mono<Void> clearAllCache();

    /**
     * 获取当前缓存大小
     * @return 缓存key数量
     */
    Mono<Long> getCacheSize();

    /**
     * 延长缓存TTL
     * @param authKey 认证key
     * @param ttl 新的过期时间
     * @return 是否成功
     */
    Mono<Boolean> extendCacheTTL(String authKey, Duration ttl);
}