package org.jdt.mcp.gateway.proxy.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.proxy.service.MCPDiscoveryService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class MCPDiscoveryServiceImpl implements MCPDiscoveryService {
    private final MCPServiceMapper mcpServiceMapper;
    private final ConcurrentMap<String, MCPServiceEntity> serviceCache = new ConcurrentHashMap<>();

    public MCPDiscoveryServiceImpl(MCPServiceMapper mcpServiceMapper) {
        this.mcpServiceMapper = mcpServiceMapper;
    }

    @PostConstruct
    public void initializeServiceCache() {
        refreshServiceCache();
    }

    @Override
    public Mono<MCPServiceEntity> getService(String serviceId) {
        MCPServiceEntity cachedService = serviceCache.get(serviceId);

        if (cachedService != null && cachedService.getStatus() == ServiceStatus.ACTIVE) {
            return Mono.just(cachedService);
        }

        // 缓存中没有或状态不是ACTIVE，从数据库重新加载
        return Mono.fromCallable(() -> mcpServiceMapper.findByServiceId(serviceId))
                .doOnNext(service -> {
                    if (service != null) {
                        serviceCache.put(serviceId, service);
                        log.debug("Cached service: {}", serviceId);
                    }
                })
                .filter(service -> service != null && service.getStatus() == ServiceStatus.ACTIVE);
    }

    @Override
    public Flux<MCPServiceEntity> getAllActiveServices() {
        return Flux.fromIterable(serviceCache.values())
                .filter(service -> service.getStatus() == ServiceStatus.ACTIVE);
    }

    @Override
    public boolean isServiceActive(String serviceId) {
        MCPServiceEntity service = serviceCache.get(serviceId);
        return service != null && service.getStatus() == ServiceStatus.ACTIVE;
    }

    @Override
    public void refreshServiceCache() {
        try {
            var services = mcpServiceMapper.findByStatus(ServiceStatus.ACTIVE);
            serviceCache.clear();
            services.forEach(service -> {
                serviceCache.put(service.getServiceId(), service);
                log.debug("Loaded service into cache: {}", service.getServiceId());
            });
            log.info("Service cache refreshed, loaded {} services", services.size());
        } catch (Exception e) {
            log.error("Failed to refresh service cache", e);
        }
    }
}
