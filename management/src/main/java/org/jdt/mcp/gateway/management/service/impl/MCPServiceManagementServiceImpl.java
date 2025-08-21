package org.jdt.mcp.gateway.management.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.management.dto.MCPServiceCreateRequest;
import org.jdt.mcp.gateway.management.dto.MCPServiceUpdateRequest;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.management.service.MCPServiceManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional
public class MCPServiceManagementServiceImpl implements MCPServiceManagementService {

    private final MCPServiceMapper serviceMapper;
    private final RestTemplate restTemplate;

    public MCPServiceManagementServiceImpl(MCPServiceMapper serviceMapper, RestTemplate restTemplate) {
        this.serviceMapper = serviceMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public MCPServiceEntity createService(MCPServiceCreateRequest request) {
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
    }

    @Override
    public MCPServiceEntity updateService(String serviceId, MCPServiceUpdateRequest request) {
        MCPServiceEntity existing = getServiceByServiceId(serviceId);

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
    }

    @Override
    public void deleteService(String serviceId) {
        MCPServiceEntity existing = getServiceByServiceId(serviceId);
        serviceMapper.deleteById(existing.getId());
        log.info("Deleted MCP service: {}", serviceId);
    }

    @Override
    public MCPServiceEntity getServiceByServiceId(String serviceId) {
        MCPServiceEntity service = serviceMapper.findByServiceId(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + serviceId);
        }
        return service;
    }

    @Override
    public Page<MCPServiceEntity> getServices(ServiceStatus status, String name, Pageable pageable) {
        List<MCPServiceEntity> services = serviceMapper.findByConditions(status, name,
                (int) pageable.getOffset(), pageable.getPageSize());
        long total = serviceMapper.countByConditions(status, name);
        return new PageImpl<>(services, pageable, total);
    }

    @Override
    public List<MCPServiceEntity> getActiveServices() {
        return serviceMapper.findByStatus(ServiceStatus.ACTIVE);
    }

    @Override
    public MCPServiceEntity updateServiceStatus(String serviceId, ServiceStatus status) {
        MCPServiceEntity service = getServiceByServiceId(serviceId);
        service.setStatus(status);
        service.setUpdatedAt(LocalDateTime.now());
        serviceMapper.update(service);
        log.info("Updated service {} status to {}", serviceId, status);
        return service;
    }

    @Override
    public boolean performHealthCheck(String serviceId) {
        MCPServiceEntity service = getServiceByServiceId(serviceId);
        String healthCheckUrl = service.getHealthCheckUrl();

        if (healthCheckUrl == null || healthCheckUrl.trim().isEmpty()) {
            // 如果没有健康检查URL，直接ping主endpoint
            healthCheckUrl = service.getEndpoint() + "/health";
        }

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(healthCheckUrl, String.class);
            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            log.info("Health check for service {}: {}", serviceId, isHealthy ? "HEALTHY" : "UNHEALTHY");

            // 根据健康检查结果更新服务状态
            if (!isHealthy && service.getStatus() == ServiceStatus.ACTIVE) {
                updateServiceStatus(serviceId, ServiceStatus.MAINTENANCE);
            } else if (isHealthy && service.getStatus() == ServiceStatus.MAINTENANCE) {
                updateServiceStatus(serviceId, ServiceStatus.ACTIVE);
            }

            return isHealthy;
        } catch (Exception e) {
            log.error("Health check failed for service {}: {}", serviceId, e.getMessage());
            updateServiceStatus(serviceId, ServiceStatus.MAINTENANCE);
            return false;
        }
    }
}