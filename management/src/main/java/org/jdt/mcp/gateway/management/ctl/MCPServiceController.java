package org.jdt.mcp.gateway.management.ctl;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.core.dto.MCPServiceCreateRequest;
import org.jdt.mcp.gateway.core.dto.MCPServiceUpdateRequest;
import org.jdt.mcp.gateway.management.service.MCPServiceManagementService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Mono<MCPServiceEntity> createService(@Valid @RequestBody MCPServiceCreateRequest request) {
        log.info("Creating MCP service: {}", request.getName());
        return serviceManagementService.createService(request);
    }

    /**
     * 更新MCP服务
     */
    @PutMapping("/{serviceId}")
    public Mono<MCPServiceEntity> updateService(
            @PathVariable String serviceId,
            @Valid @RequestBody MCPServiceUpdateRequest request) {
        log.info("Updating MCP service: {}", serviceId);
        return serviceManagementService.updateService(serviceId, request);
    }

    /**
     * 删除MCP服务
     */
    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteService(@PathVariable String serviceId) {
        log.info("Deleting MCP service: {}", serviceId);
        return serviceManagementService.deleteService(serviceId);
    }

    /**
     * 根据ID获取MCP服务
     */
    @GetMapping("/{serviceId}")
    public Mono<MCPServiceEntity> getService(@PathVariable String serviceId) {
        return serviceManagementService.getServiceByServiceId(serviceId);
    }

    /**
     * 分页查询MCP服务列表
     */
    @GetMapping
    public Mono<org.springframework.data.domain.Page<MCPServiceEntity>> getServices(
            @RequestParam(required = false) ServiceStatus status,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return serviceManagementService.getServices(status, name, pageable);
    }

    /**
     * 获取所有激活的服务（用于配置生成）
     */
    @GetMapping("/active")
    public Flux<MCPServiceEntity> getActiveServices() {
        return serviceManagementService.getActiveServices();
    }

    /**
     * 更新服务状态
     */
    @PatchMapping("/{serviceId}/status")
    public Mono<MCPServiceEntity> updateServiceStatus(
            @PathVariable String serviceId,
            @RequestParam ServiceStatus status) {
        log.info("Updating service {} status to {}", serviceId, status);
        return serviceManagementService.updateServiceStatus(serviceId, status);
    }

    /**
     * 服务健康检查
     */
    @PostMapping("/{serviceId}/health-check")
    public Mono<Boolean> healthCheck(@PathVariable String serviceId) {
        return serviceManagementService.performHealthCheck(serviceId);
    }
}