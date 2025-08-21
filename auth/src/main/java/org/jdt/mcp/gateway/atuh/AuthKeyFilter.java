package org.jdt.mcp.gateway.atuh;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.atuh.service.AuthService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.jdt.mcp.gateway.atuh.tool.AuthReqTool.*;

/**
 * authKey过滤器
 * 只会过滤基于webflux的请求
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthKeyFilter implements WebFilter {

    private final AuthService authService;

    public AuthKeyFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain ) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 提取认证key
        String authKey = extractAuthKey(request);
        String ip = getClientIp(request);
        String connectionId = generateConnectionId(request);


        log.debug("Processing request for path: {}, connectionId: {}, key: {}"
                , path, connectionId, authKey);

        // todo 传递验证信息，后续的如何从context里拿到鉴权信息
        // 验证认证key
        return authService.integrationValidate(path,ip,authKey)
                .flatMap(isValid -> {
                    if (isValid) {
                        log.info("Authentication successful for connectionId: {}", connectionId);
                        // 认证成功，将信息传递到下游
                        return chain.filter(exchange);
                    } else {
                        log.warn("Authentication failed for connectionId: {}", connectionId);
                        // 认证失败，返回403
                        return handleUnauthorized(exchange,"鉴权失败，请在智能研发门户申请key");
                    }
                })
                .onErrorResume(throwable -> {
                    log.error("Authentication error for connectionId: {}", connectionId, throwable);
                    return handleUnauthorized(exchange,"鉴权服务失败");
                });
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json");

        String errorResponse = String.format("{\"error\": \"%s\", \"code\": 403}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
    }

}