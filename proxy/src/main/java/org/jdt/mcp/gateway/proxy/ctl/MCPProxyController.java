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
     * 获取服务实时统计信息（从Redis缓存）
     */
    @GetMapping(value = "/stats/{serviceId}/realtime", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getRealtimeServiceStats(@PathVariable String serviceId) {
        return statisticsService.getRealtimeServiceStats(serviceId)
                .map(stats -> Map.of(
                        "serviceId", serviceId,
                        "source", "realtime",
                        "totalCalls", stats.getTotalCalls(),
                        "successCalls", stats.getSuccessCalls(),
                        "failedCalls", stats.getFailedCalls(),
                        "successRate", stats.getSuccessRate(),
                        "averageResponseTime", stats.getAverageResponseTime(),
                        "maxResponseTime", stats.getMaxResponseTime(),
                        "uniqueUsers", stats.getUniqueUsers(),
                        "lastCallTime", stats.getLastCallTime() != null ? stats.getLastCallTime().toString() : null
                ));
    }

    /**
     * 获取服务统计信息（从数据库）
     */
    @GetMapping(value = "/stats/{serviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getServiceStats(@PathVariable String serviceId) {
        return statisticsService.getServiceStats(serviceId)
                .map(stats -> Map.of(
                        "serviceId", serviceId,
                        "source", "database",
                        "totalCalls", stats.getTotalCalls(),
                        "successCalls", stats.getSuccessCalls(),
                        "failedCalls", stats.getFailedCalls(),
                        "successRate", stats.getSuccessRate(),
                        "averageResponseTime", stats.getAverageResponseTime(),
                        "maxResponseTime", stats.getMaxResponseTime(),
                        "uniqueUsers", stats.getUniqueUsers(),
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
     * 手动刷新统计数据到数据库
     */
    @PostMapping(value = "/stats/flush", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> flushStatistics() {
        return statisticsService.flushStatisticsToDatabase()
                .then(Mono.just(Map.of("status", "success", "message", "Statistics flushed to database")))
                .onErrorReturn(Map.of("status", "error", "message", "Failed to flush statistics"));
    }

    /**
     * 清理统计缓存
     */
    @DeleteMapping(value = "/stats/cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> clearStatsCache() {
        return statisticsService.clearStats()
                .then(Mono.just(Map.of("status", "success", "message", "Statistics cache cleared")))
                .onErrorReturn(Map.of("status", "error", "message", "Failed to clear statistics cache"));
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