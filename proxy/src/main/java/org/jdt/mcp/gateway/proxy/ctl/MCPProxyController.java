package org.jdt.mcp.gateway.proxy.ctl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.proxy.McpProxyHandler;
import org.jdt.mcp.gateway.proxy.service.MCPDiscoveryService;
import org.jdt.mcp.gateway.proxy.service.StatisticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mcp")
public class MCPProxyController {

    private final McpProxyHandler proxyHandler;
    private final MCPDiscoveryService mcpDiscoveryService;
    private final StatisticsService statisticsService;

    public MCPProxyController(McpProxyHandler proxyHandler,
                              MCPDiscoveryService mcpDiscoveryService,
                              StatisticsService statisticsService) {
        this.proxyHandler = proxyHandler;
        this.mcpDiscoveryService = mcpDiscoveryService;
        this.statisticsService = statisticsService;
    }

    /**
     * 代理所有MCP请求
     */
    @RequestMapping(value = "/{serviceId}/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS
    })
    public Mono<Void> proxyRequest(ServerWebExchange exchange) {
        return proxyHandler.handleProxy(exchange);
    }

    /**
     * 获取服务统计信息
     */
    @GetMapping(value = "/stats/{serviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getServiceStats(@PathVariable String serviceId) {
        StatisticsService.ServiceStats stats = statisticsService.getServiceStats(serviceId);

        return Mono.just(Map.of(
                "serviceId", serviceId,
                "totalCalls", stats.getTotalCalls(),
                "successCalls", stats.getSuccessCalls(),
                "failedCalls", stats.getFailedCalls(),
                "successRate", stats.getSuccessRate(),
                "averageResponseTime", stats.getAverageResponseTime(),
                "maxResponseTime", stats.getMaxResponseTime(),
                "lastCallTime", stats.getLastCallTime() != null ? stats.getLastCallTime().toString() : null
        ));
    }

    /**
     * 获取所有服务状态
     */
    @GetMapping(value = "/services", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getAllServices() {
        return mcpDiscoveryService.getAllActiveServices()
                .collectList()
                .map(services -> Map.of(
                        "services", services,
                        "count", services.size()
                ));
    }

    /**
     * 刷新服务缓存
     */
    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> refreshServices() {
        return Mono.fromRunnable(mcpDiscoveryService::refreshServiceCache)
                .then(Mono.just(Map.of("status", "success", "message", "Service cache refreshed")));
    }

    /**
     * 健康检查端点
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of(
                "status", "UP",
                "service", "MCP Gateway Proxy",
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
