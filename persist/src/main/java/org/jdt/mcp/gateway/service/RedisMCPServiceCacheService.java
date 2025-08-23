package org.jdt.mcp.gateway.service;

import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RedisMCPServiceCacheService {

    /**
     * 从缓存获取服务信息
     */
    Mono<MCPServiceEntity> getServiceFromCache(String serviceId);

    /**
     * 缓存单个服务
     */
    Mono<Void> cacheService(MCPServiceEntity service);

    /**
     * 从缓存移除服务
     */
    Mono<Void> removeServiceFromCache(String serviceId);

    /**
     * 获取所有活跃服务
     */
    Flux<MCPServiceEntity> getAllActiveServicesFromCache();

    /**
     * 检查服务是否活跃
     */
    Mono<Boolean> isServiceActive(String serviceId);

    /**
     * 刷新服务缓存
     */
    Mono<Void> refreshServiceCache(List<MCPServiceEntity> activeServices);

    /**
     * 清空所有服务缓存
     */
    Mono<Void> clearAllServiceCache();

    /**
     * 获取缓存中的服务数量
     */
    Mono<Long> getCachedServiceCount();

    /**
     * 检查服务是否在缓存中
     */
    Mono<Boolean> isServiceCached(String serviceId);

    /**
     * 获取服务缓存的TTL信息
     */
    Mono<Long> getServiceCacheTTL(String serviceId);
}