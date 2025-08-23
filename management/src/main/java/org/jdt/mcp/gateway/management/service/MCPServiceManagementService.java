package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;

import org.jdt.mcp.gateway.core.dto.MCPServiceCreateRequest;
import org.jdt.mcp.gateway.core.dto.MCPServiceUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface MCPServiceManagementService {
    MCPServiceEntity createService(MCPServiceCreateRequest request);
    MCPServiceEntity updateService(String serviceId, MCPServiceUpdateRequest request);
    void deleteService(String serviceId);
    MCPServiceEntity getServiceByServiceId(String serviceId);
    Page<MCPServiceEntity> getServices(ServiceStatus status, String name, Pageable pageable);
    List<MCPServiceEntity> getActiveServices();
    MCPServiceEntity updateServiceStatus(String serviceId, ServiceStatus status);
    boolean performHealthCheck(String serviceId);
}
