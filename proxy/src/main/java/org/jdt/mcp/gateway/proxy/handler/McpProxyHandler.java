package org.jdt.mcp.gateway.proxy.handler;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.auth.service.SessionAuthService;
import org.jdt.mcp.gateway.auth.tool.AuthContextHelper;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class McpProxyHandler {

    private final WebClient webClient;
    private final MCPDiscoveryService mcpDiscoveryService;
    private final StatisticsService statisticsService;
    private final AuthContextHelper authContextHelper;
    private final SessionAuthService sessionAuthService;

    // 需要过滤的请求头
    private static final List<String> FILTERED_HEADERS = List.of(
            "host", "content-length", "connection", "upgrade",
            "proxy-connection", "proxy-authorization", "te", "trailers", "transfer-encoding"
    );

    // 用于匹配响应中sessionId的正则表达式
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("sessionId=([a-f0-9\\-]{36})");

    // SSE响应的默认会话过期时间：2小时
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(2);

    public McpProxyHandler(WebClient webClient,
                           MCPDiscoveryService mcpDiscoveryService,
                           StatisticsService statisticsService,
                           AuthContextHelper authContextHelper,
                           SessionAuthService sessionAuthService) {
        this.webClient = webClient;
        this.mcpDiscoveryService = mcpDiscoveryService;
        this.statisticsService = statisticsService;
        this.authContextHelper = authContextHelper;
        this.sessionAuthService = sessionAuthService;
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

        // 获取认证信息
        String authKey = (String) exchange.getAttributes().get("authKey");

        // 获取响应体 Flux
        Flux<DataBuffer> body = clientResponse.bodyToFlux(DataBuffer.class);

        // 处理响应内容，提取sessionId并建立关联关系
        body = processResponseBody(body, authKey, exchange);

        // 流式复制响应体
        return response.writeWith(body);
    }

    /**
     * 处理响应体，提取sessionId并建立与authKey的关联关系
     */
    private Flux<DataBuffer> processResponseBody(Flux<DataBuffer> body, String authKey, ServerWebExchange exchange) {
        if (authKey == null) {
            log.debug("No authKey found, skipping sessionId extraction");
            return body;
        }

        return body.doOnNext(buffer -> {
                    try {
                        // 将 DataBuffer 转换为字符串进行处理
                        String content = buffer.toString(StandardCharsets.UTF_8);
                        log.debug("Processing response content for authKey {}: {}", maskKey(authKey), content);

                        // 提取sessionId
                        extractAndStoreSessionId(content, authKey, exchange);

                    } catch (Exception e) {
                        log.error("Error processing response content for sessionId extraction: {}", e.getMessage());
                    }
                }).doOnComplete(() -> log.debug("Response streaming completed"))
                .doOnError(throwable -> log.error("Error during response streaming: {}", throwable.getMessage()));
    }

    /**
     * 从响应内容中提取sessionId并存储关联关系
     */
    private void extractAndStoreSessionId(String content, String authKey, ServerWebExchange exchange) {
        Matcher matcher = SESSION_ID_PATTERN.matcher(content);

        if (matcher.find()) {
            String sessionId = matcher.group(1);
            log.info("Extracted sessionId: {} for authKey: {}", sessionId, maskKey(authKey));

            // 异步存储sessionId与authKey的关联关系
            sessionAuthService.storeSessionAuthKey(sessionId, authKey, DEFAULT_SESSION_TTL)
                    .doOnSuccess(v -> {
                        log.info("Successfully stored session mapping: {} -> {}",
                                sessionId, maskKey(authKey));
                        // 将sessionId也存储到exchange attributes中，供后续使用
                        exchange.getAttributes().put("extractedSessionId", sessionId);
                    })
                    .doOnError(error -> log.error("Failed to store session mapping for sessionId: {}",
                            sessionId, error))
                    .subscribe(); // 异步执行，不阻塞主流程
        } else {
            log.debug("No sessionId found in response content for authKey: {}", maskKey(authKey));
        }
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

    /**
     * 脱敏显示key
     */
    private String maskKey(String authKey) {
        if (authKey == null || authKey.length() < 4) {
            return "***";
        }
        return "***" + authKey.substring(authKey.length() - 4);
    }
}