package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.core.dto.MCPServiceCreateRequest;
import org.jdt.mcp.gateway.core.dto.MCPServiceUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MCPServiceManagementService {
    Mono<MCPServiceEntity> createService(MCPServiceCreateRequest request);
    Mono<MCPServiceEntity> updateService(String serviceId, MCPServiceUpdateRequest request);
    Mono<Void> deleteService(String serviceId);
    Mono<MCPServiceEntity> getServiceByServiceId(String serviceId);
    Mono<Page<MCPServiceEntity>> getServices(ServiceStatus status, String name, Pageable pageable);
    Flux<MCPServiceEntity> getActiveServices();
    Mono<MCPServiceEntity> updateServiceStatus(String serviceId, ServiceStatus status);
    Mono<Boolean> performHealthCheck(String serviceId);
}