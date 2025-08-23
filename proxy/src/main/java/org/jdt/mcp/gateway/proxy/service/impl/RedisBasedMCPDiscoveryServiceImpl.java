package org.jdt.mcp.gateway.proxy.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.proxy.service.MCPDiscoveryService;
import org.jdt.mcp.gateway.service.RedisMCPServiceCacheService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class RedisBasedMCPDiscoveryServiceImpl implements MCPDiscoveryService {

    private final MCPServiceMapper mcpServiceMapper;
    private final RedisMCPServiceCacheService redisCacheService;

    public RedisBasedMCPDiscoveryServiceImpl(MCPServiceMapper mcpServiceMapper,
                                             RedisMCPServiceCacheService redisCacheService) {
        this.mcpServiceMapper = mcpServiceMapper;
        this.redisCacheService = redisCacheService;
    }

    @PostConstruct
    public void initializeServiceCache() {
        refreshServiceCache();
    }

    @Override
    public Mono<MCPServiceEntity> getService(String serviceId) {
        return redisCacheService.getServiceFromCache(serviceId)
                .filter(service -> service.getStatus() == ServiceStatus.ACTIVE)
                .switchIfEmpty(loadServiceFromDatabase(serviceId))
                .doOnNext(service -> log.debug("Retrieved service: {}", serviceId))
                .doOnError(error -> log.warn("Error retrieving service {}: {}", serviceId, error.getMessage()));
    }

    @Override
    public Flux<MCPServiceEntity> getAllActiveServices() {
        return redisCacheService.getAllActiveServicesFromCache()
                .switchIfEmpty(loadActiveServicesFromDatabase())
                .doOnError(error -> log.warn("Error getting active services: {}", error.getMessage()));
    }

    @Override
    public boolean isServiceActive(String serviceId) {
        try {
            // 尝试从缓存检查
            Boolean isActive = redisCacheService.isServiceActive(serviceId)
                    .block(Duration.ofSeconds(1));

            if (Boolean.TRUE.equals(isActive)) {
                return true;
            }

            // 缓存未命中，降级到数据库查询
            return fallbackToDatabase(serviceId);

        } catch (Exception e) {
            log.warn("Error checking service active status for {}: {}", serviceId, e.getMessage());
            return fallbackToDatabase(serviceId);
        }
    }

    @Override
    public void refreshServiceCache() {
        Mono.fromRunnable(() -> {
            try {
                List<MCPServiceEntity> activeServices = mcpServiceMapper.findByStatus(ServiceStatus.ACTIVE);

                redisCacheService.refreshServiceCache(activeServices)
                        .doOnSuccess(v -> log.info("Service cache refreshed, loaded {} active services", activeServices.size()))
                        .doOnError(error -> log.error("Failed to refresh service cache", error))
                        .subscribe();

            } catch (Exception e) {
                log.error("Failed to load active services from database for cache refresh", e);
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
                        redisCacheService.cacheService(service).subscribe();
                        log.debug("Loaded and cached service from database: {}", serviceId);
                    }
                })
                .filter(service -> service != null && service.getStatus() == ServiceStatus.ACTIVE);
    }

    /**
     * 从数据库加载所有活跃服务
     */
    private Flux<MCPServiceEntity> loadActiveServicesFromDatabase() {
        return Mono.fromCallable(() -> mcpServiceMapper.findByStatus(ServiceStatus.ACTIVE))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .doOnNext(service -> {
                    // 异步缓存每个服务
                    redisCacheService.cacheService(service).subscribe();
                });
    }

    /**
     * 数据库降级查询
     */
    private boolean fallbackToDatabase(String serviceId) {
        try {
            MCPServiceEntity service = mcpServiceMapper.findByServiceId(serviceId);
            boolean isActive = service != null && service.getStatus() == ServiceStatus.ACTIVE;

            if (service != null) {
                // 异步更新缓存
                redisCacheService.cacheService(service).subscribe();
            }

            return isActive;
        } catch (Exception dbError) {
            log.error("Database fallback failed for service {}: {}", serviceId, dbError.getMessage());
            return false;
        }
    }

    /**
     * 更新单个服务缓存
     */
    public Mono<Void> updateServiceCache(String serviceId) {
        return Mono.fromCallable(() -> mcpServiceMapper.findByServiceId(serviceId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(service -> {
                    if (service == null) {
                        return redisCacheService.removeServiceFromCache(serviceId);
                    } else {
                        return redisCacheService.cacheService(service);
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
     * 移除服务缓存
     */
    public Mono<Void> removeServiceFromCache(String serviceId) {
        return redisCacheService.removeServiceFromCache(serviceId)
                .doOnSuccess(v -> log.info("Removed service from cache: {}", serviceId))
                .doOnError(error -> log.warn("Error removing service from cache: {}", serviceId, error));
    }

    /**
     * 获取缓存中的服务数量
     */
    public Mono<Long> getCachedServiceCount() {
        return redisCacheService.getCachedServiceCount()
                .doOnNext(count -> log.debug("Current cached service count: {}", count));
    }

    /**
     * 检查服务是否在缓存中
     */
    public Mono<Boolean> isServiceCached(String serviceId) {
        return redisCacheService.isServiceCached(serviceId);
    }

    /**
     * 清空所有服务缓存
     */
    public Mono<Void> clearAllServiceCache() {
        return redisCacheService.clearAllServiceCache()
                .doOnSuccess(v -> log.info("Cleared all service caches"))
                .doOnError(error -> log.error("Failed to clear service caches", error));
    }

    /**
     * 获取服务缓存的TTL信息
     */
    public Mono<Long> getServiceCacheTTL(String serviceId) {
        return redisCacheService.getServiceCacheTTL(serviceId)
                .doOnNext(ttl -> log.debug("Service {} cache TTL: {}", serviceId, ttl));
    }
}