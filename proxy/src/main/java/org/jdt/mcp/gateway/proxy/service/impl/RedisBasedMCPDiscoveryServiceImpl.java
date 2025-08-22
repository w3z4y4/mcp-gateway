package org.jdt.mcp.gateway.proxy.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.proxy.service.MCPDiscoveryService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class RedisBasedMCPDiscoveryServiceImpl implements MCPDiscoveryService {

    private static final String SERVICE_CACHE_KEY_PREFIX = "mcp:service:";
    private static final String ACTIVE_SERVICES_SET_KEY = "mcp:active_services";
    private static final Duration CACHE_EXPIRE = Duration.ofMinutes(30);

    private final MCPServiceMapper mcpServiceMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisBasedMCPDiscoveryServiceImpl(MCPServiceMapper mcpServiceMapper,
                                             ReactiveStringRedisTemplate redisTemplate,
                                             ObjectMapper objectMapper) {
        this.mcpServiceMapper = mcpServiceMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializeServiceCache() {
        refreshServiceCache();
    }

    @Override
    public Mono<MCPServiceEntity> getService(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;

        return redisTemplate.opsForValue().get(cacheKey)
                .map(this::deserializeService)
                .filter(service -> service != null && service.getStatus() == ServiceStatus.ACTIVE)
                .switchIfEmpty(loadServiceFromDatabase(serviceId))
                .doOnNext(service -> log.debug("Retrieved service from cache: {}", serviceId))
                .doOnError(error -> log.warn("Error retrieving service {}: {}", serviceId, error.getMessage()));
    }

    @Override
    public Flux<MCPServiceEntity> getAllActiveServices() {
        return redisTemplate.opsForSet().members(ACTIVE_SERVICES_SET_KEY)
                .flatMap(this::getService)
                .filter(service -> service.getStatus() == ServiceStatus.ACTIVE)
                .doOnError(error -> log.warn("Error getting active services: {}", error.getMessage()));
    }

    @Override
    public boolean isServiceActive(String serviceId) {
        try {
            // 同步检查Redis中的服务状态
            Boolean isMember = redisTemplate.opsForSet().isMember(ACTIVE_SERVICES_SET_KEY, serviceId).block(Duration.ofSeconds(1));
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("Error checking service active status for {}: {}", serviceId, e.getMessage());
            // 降级到数据库查询
            try {
                MCPServiceEntity service = mcpServiceMapper.findByServiceId(serviceId);
                return service != null && service.getStatus() == ServiceStatus.ACTIVE;
            } catch (Exception dbError) {
                log.error("Database fallback failed for service {}: {}", serviceId, dbError.getMessage());
                return false;
            }
        }
    }

    @Override
    public void refreshServiceCache() {
        Mono.fromRunnable(() -> {
            try {
                List<MCPServiceEntity> activeServices = mcpServiceMapper.findByStatus(ServiceStatus.ACTIVE);

                // 清空现有缓存
                redisTemplate.delete(ACTIVE_SERVICES_SET_KEY).subscribe();

                for (MCPServiceEntity service : activeServices) {
                    String cacheKey = SERVICE_CACHE_KEY_PREFIX + service.getServiceId();
                    String serviceJson = serializeService(service);

                    // 缓存服务详情
                    redisTemplate.opsForValue().set(cacheKey, serviceJson, CACHE_EXPIRE).subscribe();

                    // 添加到活跃服务集合
                    redisTemplate.opsForSet().add(ACTIVE_SERVICES_SET_KEY, service.getServiceId()).subscribe();
                }

                // 设置活跃服务集合的过期时间
                redisTemplate.expire(ACTIVE_SERVICES_SET_KEY, CACHE_EXPIRE).subscribe();

                log.info("Service cache refreshed, loaded {} active services", activeServices.size());
            } catch (Exception e) {
                log.error("Failed to refresh service cache", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 从数据库加载服务并缓存
     */
    private Mono<MCPServiceEntity> loadServiceFromDatabase(String serviceId) {
        return Mono.fromCallable(() -> mcpServiceMapper.findByServiceId(serviceId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(service -> {
                    if (service != null) {
                        // 异步缓存到Redis
                        cacheService(service).subscribe();
                        log.debug("Loaded and cached service from database: {}", serviceId);
                    }
                })
                .filter(service -> service != null && service.getStatus() == ServiceStatus.ACTIVE);
    }

    /**
     * 缓存单个服务
     */
    private Mono<Void> cacheService(MCPServiceEntity service) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + service.getServiceId();
        String serviceJson = serializeService(service);

        return Mono.when(
                redisTemplate.opsForValue().set(cacheKey, serviceJson, CACHE_EXPIRE),
                service.getStatus() == ServiceStatus.ACTIVE
                        ? redisTemplate.opsForSet().add(ACTIVE_SERVICES_SET_KEY, service.getServiceId())
                        : Mono.empty()
        ).then();
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

    /**
     * 移除服务缓存
     */
    public Mono<Void> removeServiceFromCache(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;

        return Mono.when(
                        redisTemplate.delete(cacheKey),
                        redisTemplate.opsForSet().remove(ACTIVE_SERVICES_SET_KEY, serviceId)
                ).then()
                .doOnSuccess(v -> log.info("Removed service from cache: {}", serviceId))
                .doOnError(error -> log.warn("Error removing service from cache: {}", serviceId, error));
    }

    /**
     * 更新单个服务缓存
     */
    public Mono<Void> updateServiceCache(String serviceId) {
        return Mono.fromCallable(() -> mcpServiceMapper.findByServiceId(serviceId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(service -> {
                    if (service == null) {
                        return removeServiceFromCache(serviceId);
                    } else {
                        return cacheService(service);
                    }
                })
                .doOnSuccess(v -> log.debug("Updated service cache: {}", serviceId))
                .doOnError(error -> log.warn("Error updating service cache: {}", serviceId, error));
    }

    /**
     * 批量更新服务缓存
     */
    public Mono<Void> updateMultipleServiceCache(List<String> serviceIds) {
        return Flux.fromIterable(serviceIds)
                .flatMap(this::updateServiceCache)
                .then()
                .doOnSuccess(v -> log.info("Updated multiple service caches: {}", serviceIds))
                .doOnError(error -> log.warn("Error updating multiple service caches", error));
    }

    /**
     * 获取缓存中的服务数量
     */
    public Mono<Long> getCachedServiceCount() {
        return redisTemplate.opsForSet().size(ACTIVE_SERVICES_SET_KEY)
                .doOnNext(count -> log.debug("Current cached service count: {}", count));
    }

    /**
     * 检查服务是否在缓存中
     */
    public Mono<Boolean> isServiceCached(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;
        return redisTemplate.hasKey(cacheKey);
    }

    /**
     * 清空所有服务缓存
     */
    public Mono<Void> clearAllServiceCache() {
        String pattern = SERVICE_CACHE_KEY_PREFIX + "*";
        return Mono.when(
                        redisTemplate.keys(pattern).flatMap(redisTemplate::delete),
                        redisTemplate.delete(ACTIVE_SERVICES_SET_KEY)
                ).then()
                .doOnSuccess(v -> log.info("Cleared all service caches"))
                .doOnError(error -> log.error("Failed to clear service caches", error));
    }

    /**
     * 获取服务缓存的TTL信息
     */
    public Mono<Long> getServiceCacheTTL(String serviceId) {
        String cacheKey = SERVICE_CACHE_KEY_PREFIX + serviceId;
        return redisTemplate.getExpire(cacheKey)
                .map(Duration::getSeconds)
                .doOnNext(ttl -> log.debug("Service {} cache TTL: {}", serviceId, ttl));
    }
}