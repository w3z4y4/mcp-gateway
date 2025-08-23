package org.jdt.mcp.gateway.management.ctl;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.core.dto.MCPServiceCreateRequest;
import org.jdt.mcp.gateway.core.dto.MCPServiceUpdateRequest;
import org.jdt.mcp.gateway.management.service.MCPServiceManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/management/services")
@Slf4j
public class MCPServiceController {

    private final MCPServiceManagementService serviceManagementService;

    public MCPServiceController(MCPServiceManagementService serviceManagementService) {
        this.serviceManagementService = serviceManagementService;
    }

    /**
     * 创建MCP服务
     */
    @PostMapping
    public ResponseEntity<MCPServiceEntity> createService(@Valid @RequestBody MCPServiceCreateRequest request) {
        log.info("Creating MCP service: {}", request.getName());
        MCPServiceEntity service = serviceManagementService.createService(request);
        return ResponseEntity.ok(service);
    }

    /**
     * 更新MCP服务
     */
    @PutMapping("/{serviceId}")
    public ResponseEntity<MCPServiceEntity> updateService(
            @PathVariable String serviceId,
            @Valid @RequestBody MCPServiceUpdateRequest request) {
        log.info("Updating MCP service: {}", serviceId);
        MCPServiceEntity service = serviceManagementService.updateService(serviceId, request);
        return ResponseEntity.ok(service);
    }

    /**
     * 删除MCP服务
     */
    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(@PathVariable String serviceId) {
        log.info("Deleting MCP service: {}", serviceId);
        serviceManagementService.deleteService(serviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 根据ID获取MCP服务
     */
    @GetMapping("/{serviceId}")
    public ResponseEntity<MCPServiceEntity> getService(@PathVariable String serviceId) {
        MCPServiceEntity service = serviceManagementService.getServiceByServiceId(serviceId);
        return ResponseEntity.ok(service);
    }

    /**
     * 分页查询MCP服务列表
     */
    @GetMapping
    public ResponseEntity<Page<MCPServiceEntity>> getServices(
            @RequestParam(required = false) ServiceStatus status,
            @RequestParam(required = false) String name,
            Pageable pageable) {
        Page<MCPServiceEntity> services = serviceManagementService.getServices(status, name, pageable);
        return ResponseEntity.ok(services);
    }

    /**
     * 获取所有激活的服务（用于配置生成）
     */
    @GetMapping("/active")
    public ResponseEntity<List<MCPServiceEntity>> getActiveServices() {
        List<MCPServiceEntity> services = serviceManagementService.getActiveServices();
        return ResponseEntity.ok(services);
    }

    /**
     * 更新服务状态
     */
    @PatchMapping("/{serviceId}/status")
    public ResponseEntity<MCPServiceEntity> updateServiceStatus(
            @PathVariable String serviceId,
            @RequestParam ServiceStatus status) {
        log.info("Updating service {} status to {}", serviceId, status);
        MCPServiceEntity service = serviceManagementService.updateServiceStatus(serviceId, status);
        return ResponseEntity.ok(service);
    }

    /**
     * 服务健康检查
     */
    @PostMapping("/{serviceId}/health-check")
    public ResponseEntity<Boolean> healthCheck(@PathVariable String serviceId) {
        boolean isHealthy = serviceManagementService.performHealthCheck(serviceId);
        return ResponseEntity.ok(isHealthy);
    }
}