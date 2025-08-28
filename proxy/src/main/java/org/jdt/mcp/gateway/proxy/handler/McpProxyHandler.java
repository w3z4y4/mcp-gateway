package org.jdt.mcp.gateway.proxy.handler;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.auth.tool.AuthContextHelper;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.proxy.service.MCPDiscoveryService;
import org.jdt.mcp.gateway.proxy.service.StatisticsService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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

    // 需要过滤的请求头
    private static final List<String> FILTERED_HEADERS = List.of(
            "host", "content-length", "connection", "upgrade",
            "proxy-connection", "proxy-authorization", "te", "trailers", "transfer-encoding"
    );

    // 用于匹配响应中sessionId的正则表达式
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("sessionId=([a-f0-9\\-]{36})");

    // 用于匹配需要重写的URL模式，例如：data:/mcp/message?sessionId=xxx
    private static final Pattern URL_REWRITE_PATTERN = Pattern.compile("(data:)?/mcp/([^?\\s]+)(\\?[^\\s]*)?(\\s|$|\"|\')");

    // SSE响应的默认会话过期时间：2小时
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(2);

    public McpProxyHandler(WebClient webClient,
                           MCPDiscoveryService mcpDiscoveryService,
                           StatisticsService statisticsService,
                           AuthContextHelper authContextHelper) {
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

        // 获取认证信息
        String authKey = (String) exchange.getAttributes().get("authKey");

        // 获取响应体 Flux
        Flux<DataBuffer> body = clientResponse.bodyToFlux(DataBuffer.class);

        // 处理响应内容，提取sessionId并重写URL
        body = processResponseBodyWithUrlRewrite(body, authKey, serviceId, exchange);

        // 流式复制响应体
        return response.writeWith(body);
    }

    /**
     * 处理响应体，提取sessionId并重写URL路径
     */
    private Flux<DataBuffer> processResponseBodyWithUrlRewrite(Flux<DataBuffer> body, String authKey,
                                                               String serviceId, ServerWebExchange exchange) {
        if (authKey == null) {
            log.debug("No authKey found, skipping sessionId extraction but still rewriting URLs");
        }

        return body.map(buffer -> {
                    try {
                        // 将 DataBuffer 转换为字符串进行处理
                        String content = DataBufferUtils.retain(buffer).toString(StandardCharsets.UTF_8);
                        log.debug("Processing response content for service {}: {}", serviceId, content);


                        // 重写URL路径
                        String rewrittenContent = rewriteUrlPaths(content, serviceId);

                        if (!content.equals(rewrittenContent)) {
                            log.debug("URL rewritten from: {} to: {}", content, rewrittenContent);

                            // 释放原buffer并创建新的buffer
                            DataBufferUtils.release(buffer);
                            return exchange.getResponse().bufferFactory().wrap(rewrittenContent.getBytes(StandardCharsets.UTF_8));
                        }

                        return buffer;

                    } catch (Exception e) {
                        log.error("Error processing response content for URL rewrite: {}", e.getMessage());
                        return buffer;
                    }
                }).doOnComplete(() -> log.debug("Response streaming completed"))
                .doOnError(throwable -> log.error("Error during response streaming: {}", throwable.getMessage()));
    }

    /**
     * 重写响应中的URL路径，添加serviceId前缀
     */
    private String rewriteUrlPaths(String content, String serviceId) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        Matcher matcher = URL_REWRITE_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1) != null ? matcher.group(1) : ""; // data: 前缀
            String pathPart = matcher.group(2); // /mcp/ 后的路径部分
            String queryPart = matcher.group(3) != null ? matcher.group(3) : ""; // 查询参数
            String suffix = matcher.group(4); // 结尾符号

            // 重写URL：/mcp/message -> /mcp/{serviceId}/message
            String rewrittenUrl = prefix + "/mcp/" + serviceId + "/mcp/" + pathPart + queryPart + suffix;

            log.debug("Rewriting URL: {} -> {}", matcher.group(0), rewrittenUrl);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rewrittenUrl));
        }
        matcher.appendTail(sb);

        return sb.toString();
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