package org.jdt.mcp.gateway.proxy.service;

import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MCPDiscoveryService {
    /**
     * 根据服务ID获取服务信息
     */
    Mono<MCPServiceEntity> getService(String serviceId);
    /**
     * 获取所有活跃服务
     */
    Flux<MCPServiceEntity> getAllActiveServices();
    /**
     * 检查服务是否存在且活跃
     */
    boolean isServiceActive(String serviceId);
    /**
     * 刷新服务缓存
     */
    void refreshServiceCache();
}
