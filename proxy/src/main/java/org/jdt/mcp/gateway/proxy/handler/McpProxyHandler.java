package org.jdt.mcp.gateway.proxy.handler;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.proxy.service.MCPDiscoveryService;
import org.jdt.mcp.gateway.proxy.service.StatisticsService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class McpProxyHandler {

    private final WebClient webClient;
    private final MCPDiscoveryService mcpDiscoveryService;
    private final StatisticsService statisticsService;


    // 需要过滤的请求头
    private static final List<String> FILTERED_HEADERS = List.of(
            "host", "content-length", "connection", "upgrade",
            "proxy-connection", "proxy-authorization", "te", "trailers", "transfer-encoding"
    );

    public McpProxyHandler(WebClient webClient
            , MCPDiscoveryService mcpDiscoveryService
            , StatisticsService statisticsService) {
        this.webClient = webClient;
        this.mcpDiscoveryService = mcpDiscoveryService;
        this.statisticsService = statisticsService;
    }

    /**
     * 处理MCP代理请求
     */
    public Mono<Void> handleProxy(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String path = request.getPath().value();
        log.debug("Processing proxy request: {}", path);

        // 提取服务ID（路径格式：/mcp/{serviceId}/...）
        String serviceId = extractServiceId(path);
        if (serviceId == null) {
            return handleError(response, HttpStatus.BAD_REQUEST, "Invalid path format");
        }

        Instant startTime = Instant.now();

        return mcpDiscoveryService.getService(serviceId)
                .switchIfEmpty(Mono.defer(() ->
                        handleError(response, HttpStatus.NOT_FOUND, "Service not found: " + serviceId)
                                .then(Mono.empty())))
                .flatMap(service -> proxyRequest(exchange, service, startTime))
                .onErrorResume(throwable -> {
                    log.error("Proxy error for service {}: {}", serviceId, throwable.getMessage());
                    Duration responseTime = Duration.between(startTime, Instant.now());
                    return statisticsService.recordRequest(exchange, serviceId, 500, responseTime)
                            .then(handleError(response, HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Proxy error: " + throwable.getMessage()));
                });
    }

    /**
     * 代理请求到目标服务
     */
    private Mono<Void> proxyRequest(ServerWebExchange exchange, MCPServiceEntity service, Instant startTime) {
        ServerHttpRequest request = exchange.getRequest();

        String serviceId = service.getServiceId();
        String targetUrl = buildTargetUrl(service, request);

        log.debug("Proxying request to: {}", targetUrl);

        return webClient
                .method(request.getMethod())
                .uri(targetUrl)
                .headers(headers -> copyHeaders(request.getHeaders(), headers))
                .body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToMono(clientResponse -> handleClientResponse(exchange, clientResponse, serviceId, startTime))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                        .filter(throwable -> !(throwable instanceof IllegalArgumentException)));
    }

    /**
     * 处理客户端响应
     */
    private Mono<Void> handleClientResponse(ServerWebExchange exchange, ClientResponse clientResponse,
                                            String serviceId, Instant startTime) {
        ServerHttpResponse response = exchange.getResponse();

        // 复制响应状态和头
        response.setStatusCode(clientResponse.statusCode());
        copyHeaders(clientResponse.headers().asHttpHeaders(), response.getHeaders());

        Duration responseTime = Duration.between(startTime, Instant.now());

        // 记录统计
        statisticsService.recordRequest(exchange, serviceId,
                clientResponse.statusCode().value(), responseTime);

        // 流式复制响应体
        Flux<DataBuffer> body = clientResponse.bodyToFlux(DataBuffer.class);
        return response.writeWith(body);
    }

    /**
     * 构建目标URL
     */
    private String buildTargetUrl(MCPServiceEntity service, ServerHttpRequest request) {
        String endpoint = service.getEndpoint();
        String path = request.getPath().value();

        // 移除/mcp/{serviceId}前缀
        String servicePath = path.replaceFirst("/mcp/" + service.getServiceId(), "");
        if (!servicePath.startsWith("/")) {
            servicePath = "/" + servicePath;
        }

        // 确保endpoint不以/结尾
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        String targetUrl = endpoint + servicePath;

        // 添加查询参数
        String query = request.getURI().getQuery();
        if (query != null && !query.isEmpty()) {
            targetUrl += "?" + query;
        }

        return targetUrl;
    }

    /**
     * 复制HTTP头
     */
    private void copyHeaders(HttpHeaders source, HttpHeaders target) {
        source.forEach((name, values) -> {
            if (!FILTERED_HEADERS.contains(name.toLowerCase())) {
                target.put(name, values);
            }
        });
    }

    /**
     * 从路径中提取服务ID
     */
    private String extractServiceId(String path) {
        if (!path.startsWith("/mcp/")) {
            return null;
        }

        String remaining = path.substring(5); // 移除"/mcp/"
        int slashIndex = remaining.indexOf('/');

        if (slashIndex > 0) {
            return remaining.substring(0, slashIndex);
        } else if (!remaining.isEmpty()) {
            return remaining;
        }

        return null;
    }

    /**
     * 处理错误响应
     */
    private Mono<Void> handleError(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        String errorJson = String.format(
                "{\"error\":\"%s\",\"code\":%d,\"timestamp\":\"%s\"}",
                message, status.value(), Instant.now()
        );

        DataBuffer buffer = response.bufferFactory().wrap(errorJson.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
}
