package org.jdt.mcp.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.service.RedisMCPServiceCacheService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.jdt.mcp.gateway.core.constant.RedisConstant.ACTIVE_SERVICES_SET_KEY;
import static org.jdt.mcp.gateway.core.constant.RedisConstant.SERVICE_CACHE_KEY_PREFIX;

@Slf4j
@Service
public class RedisMCPServiceCacheServiceImpl implements RedisMCPServiceCacheService {

    private static final Duration CACHE_EXPIRE = Duration.ofMinutes(30);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisMCPServiceCacheServiceImpl(ReactiveStringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<MCPServiceEntity> getServiceFromCache(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;

        return redisTemplate.opsForValue().get(cacheKey)
                .map(this::deserializeService)
                .filter(Objects::nonNull)
                .doOnNext(service -> log.debug("Retrieved service from cache: {}", serviceId))
                .doOnError(error -> log.warn("Error retrieving service from cache {}: {}", serviceId, error.getMessage()));
    }

    @Override
    public Mono<Void> cacheService(MCPServiceEntity service) {
        if (service == null) {
            return Mono.empty();
        }

        String cacheKey = SERVICE_CACHE_KEY_PREFIX + service.getServiceId();
        String serviceJson = serializeService(service);

        Mono<Void> cacheServiceData = redisTemplate.opsForValue()
                .set(cacheKey, serviceJson, CACHE_EXPIRE)
                .then();

        Mono<Void> updateActiveSet = service.getStatus() == ServiceStatus.ACTIVE
                ? redisTemplate.opsForSet().add(ACTIVE_SERVICES_SET_KEY, service.getServiceId()).then()
                : redisTemplate.opsForSet().remove(ACTIVE_SERVICES_SET_KEY, service.getServiceId()).then();

        return Mono.when(cacheServiceData, updateActiveSet)
                .doOnSuccess(v -> log.debug("Cached service: {}", service.getServiceId()))
                .doOnError(error -> log.warn("Error caching service: {}", service.getServiceId(), error));
    }

    @Override
    public Mono<Void> removeServiceFromCache(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;

        return Mono.when(
                        redisTemplate.delete(cacheKey),
                        redisTemplate.opsForSet().remove(ACTIVE_SERVICES_SET_KEY, serviceId)
                ).then()
                .doOnSuccess(v -> log.debug("Removed service from cache: {}", serviceId))
                .doOnError(error -> log.warn("Error removing service from cache: {}", serviceId, error));
    }

    @Override
    public Flux<MCPServiceEntity> getAllActiveServicesFromCache() {
        return redisTemplate.opsForSet().members(ACTIVE_SERVICES_SET_KEY)
                .flatMap(this::getServiceFromCache)
                .filter(service -> service.getStatus() == ServiceStatus.ACTIVE)
                .doOnError(error -> log.warn("Error getting active services from cache: {}", error.getMessage()));
    }

    @Override
    public Mono<Boolean> isServiceActive(String serviceId) {
        return redisTemplate.opsForSet().isMember(ACTIVE_SERVICES_SET_KEY, serviceId)
                .doOnError(error -> log.warn("Error checking service active status for {}: {}", serviceId, error.getMessage()));
    }

    @Override
    public Mono<Void> refreshServiceCache(List<MCPServiceEntity> activeServices) {
        if (activeServices == null || activeServices.isEmpty()) {
            return clearAllServiceCache();
        }

        // 清空现有的活跃服务集合
        Mono<Void> clearActiveSet = redisTemplate.delete(ACTIVE_SERVICES_SET_KEY).then();

        // 缓存所有服务并更新活跃服务集合
        Flux<Void> cacheServices = Flux.fromIterable(activeServices)
                .flatMap(this::cacheService);

        // 设置活跃服务集合的过期时间
        Mono<Void> setExpire = redisTemplate.expire(ACTIVE_SERVICES_SET_KEY, CACHE_EXPIRE).then();

        return clearActiveSet
                .then(cacheServices.then())
                .then(setExpire)
                .doOnSuccess(v -> log.info("Service cache refreshed, loaded {} active services", activeServices.size()))
                .doOnError(error -> log.error("Failed to refresh service cache", error));
    }

    @Override
    public Mono<Void> clearAllServiceCache() {
        String pattern = SERVICE_CACHE_KEY_PREFIX + "*";
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then(redisTemplate.delete(ACTIVE_SERVICES_SET_KEY))
                .then()
                .doOnSuccess(v -> log.info("Cleared all service caches"))
                .doOnError(error -> log.error("Failed to clear service caches", error));
    }

    @Override
    public Mono<Long> getCachedServiceCount() {
        return redisTemplate.opsForSet().size(ACTIVE_SERVICES_SET_KEY)
                .doOnNext(count -> log.debug("Current cached service count: {}", count));
    }

    @Override
    public Mono<Boolean> isServiceCached(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;
        return redisTemplate.hasKey(cacheKey);
    }

    @Override
    public Mono<Long> getServiceCacheTTL(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;
        return redisTemplate.getExpire(cacheKey)
                .map(Duration::getSeconds)
                .doOnNext(ttl -> log.debug("Service {} cache TTL: {}", serviceId, ttl));
    }

    /**
     * 序列化服务对象
     */
    private String serializeService(MCPServiceEntity service) {
        try {
            return objectMapper.writeValueAsString(service);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize service: {}", service.getServiceId(), e);
            throw new RuntimeException("Service serialization failed", e);
        }
    }

    /**
     * 反序列化服务对象
     */
    private MCPServiceEntity deserializeService(String serviceJson) {
        try {
            return objectMapper.readValue(serviceJson, MCPServiceEntity.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize service from cache: {}", serviceJson, e);
            return null;
        }
    }
}