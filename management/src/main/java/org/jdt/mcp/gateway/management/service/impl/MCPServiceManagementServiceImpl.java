package org.jdt.mcp.gateway.management.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.core.dto.MCPServiceCreateRequest;
import org.jdt.mcp.gateway.core.dto.MCPServiceUpdateRequest;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.management.service.MCPServiceManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional
public class MCPServiceManagementServiceImpl implements MCPServiceManagementService {

    private final MCPServiceMapper serviceMapper;
    private final WebClient webClient;

    public MCPServiceManagementServiceImpl(MCPServiceMapper serviceMapper, WebClient webClient) {
        this.serviceMapper = serviceMapper;
        this.webClient = webClient;
    }

    @Override
    public Mono<MCPServiceEntity> createService(MCPServiceCreateRequest request) {
        return Mono.fromCallable(() -> {
            // 检查serviceId是否已存在
            if (serviceMapper.findByServiceId(request.getServiceId()) != null) {
                throw new IllegalArgumentException("Service ID already exists: " + request.getServiceId());
            }

            MCPServiceEntity service = MCPServiceEntity.builder()
                    .serviceId(request.getServiceId())
                    .name(request.getName())
                    .description(request.getDescription())
                    .endpoint(request.getEndpoint())
                    .status(request.getStatus())
                    .maxQps(request.getMaxQps())
                    .healthCheckUrl(request.getHealthCheckUrl())
                    .documentation(request.getDocumentation())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            serviceMapper.insert(service);
            log.info("Created MCP service: {}", service.getServiceId());
            return service;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<MCPServiceEntity> updateService(String serviceId, MCPServiceUpdateRequest request) {
        return Mono.fromCallable(() -> {
            MCPServiceEntity existing = getServiceByServiceIdSync(serviceId);

            if (request.getName() != null) existing.setName(request.getName());
            if (request.getDescription() != null) existing.setDescription(request.getDescription());
            if (request.getEndpoint() != null) existing.setEndpoint(request.getEndpoint());
            if (request.getStatus() != null) existing.setStatus(request.getStatus());
            if (request.getMaxQps() != null) existing.setMaxQps(request.getMaxQps());
            if (request.getHealthCheckUrl() != null) existing.setHealthCheckUrl(request.getHealthCheckUrl());
            if (request.getDocumentation() != null) existing.setDocumentation(request.getDocumentation());
            existing.setUpdatedAt(LocalDateTime.now());

            serviceMapper.update(existing);
            log.info("Updated MCP service: {}", serviceId);
            return existing;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteService(String serviceId) {
        return Mono.fromRunnable(() -> {
            MCPServiceEntity existing = getServiceByServiceIdSync(serviceId);
            serviceMapper.deleteById(existing.getId());
            log.info("Deleted MCP service: {}", serviceId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<MCPServiceEntity> getServiceByServiceId(String serviceId) {
        return Mono.fromCallable(() -> getServiceByServiceIdSync(serviceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Page<MCPServiceEntity>> getServices(ServiceStatus status, String name, Pageable pageable) {
        return Mono.fromCallable(() -> {
            List<MCPServiceEntity> services = serviceMapper.findByConditions(status, name,
                    (int) pageable.getOffset(), pageable.getPageSize());
            long total = serviceMapper.countByConditions(status, name);
            return (Page<MCPServiceEntity>) new PageImpl<>(services, pageable, total);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<MCPServiceEntity> getActiveServices() {
        return Mono.fromCallable(() -> serviceMapper.findByStatus(ServiceStatus.ACTIVE))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<MCPServiceEntity> updateServiceStatus(String serviceId, ServiceStatus status) {
        return Mono.fromCallable(() -> {
            MCPServiceEntity service = getServiceByServiceIdSync(serviceId);
            service.setStatus(status);
            service.setUpdatedAt(LocalDateTime.now());
            serviceMapper.update(service);
            log.info("Updated service {} status to {}", serviceId, status);
            return service;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> performHealthCheck(String serviceId) {
        return getServiceByServiceId(serviceId)
                .flatMap(service -> {
                    String healthCheckUrl = service.getHealthCheckUrl();
                    if (healthCheckUrl == null || healthCheckUrl.trim().isEmpty()) {
                        // 如果没有健康检查URL，直接ping主endpoint
                        healthCheckUrl = service.getEndpoint() + "/health";
                    }

                    return webClient.get()
                            .uri(healthCheckUrl)
                            .retrieve()
                            .toBodilessEntity()
                            .timeout(Duration.ofSeconds(10))
                            .map(response -> {
                                boolean isHealthy = response.getStatusCode().is2xxSuccessful();
                                log.info("Health check for service {}: {}", serviceId, isHealthy ? "HEALTHY" : "UNHEALTHY");

                                // 根据健康检查结果更新服务状态（异步）
                                if (!isHealthy && service.getStatus() == ServiceStatus.ACTIVE) {
                                    updateServiceStatus(serviceId, ServiceStatus.MAINTENANCE).subscribe();
                                } else if (isHealthy && service.getStatus() == ServiceStatus.MAINTENANCE) {
                                    updateServiceStatus(serviceId, ServiceStatus.ACTIVE).subscribe();
                                }

                                return isHealthy;
                            })
                            .onErrorResume(error -> {
                                log.error("Health check failed for service {}: {}", serviceId, error.getMessage());
                                // 健康检查失败时标记为维护状态（异步）
                                updateServiceStatus(serviceId, ServiceStatus.MAINTENANCE).subscribe();
                                return Mono.just(false);
                            });
                });
    }

    private MCPServiceEntity getServiceByServiceIdSync(String serviceId) {
        MCPServiceEntity service = serviceMapper.findByServiceId(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + serviceId);
        }
        return service;
    }
}