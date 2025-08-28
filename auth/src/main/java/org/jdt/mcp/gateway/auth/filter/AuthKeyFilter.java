package org.jdt.mcp.gateway.auth.filter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.auth.service.AuthService;
import org.jdt.mcp.gateway.auth.service.SessionAuthService;
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

import static org.jdt.mcp.gateway.auth.tool.AuthReqTool.*;

/**
 * authKey过滤器
 * 只会过滤基于webflux的请求
 * 增强功能：支持sessionId与authKey的关联鉴权
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthKeyFilter implements WebFilter {

    private final AuthService authService;
    private final SessionAuthService sessionAuthService;

    public AuthKeyFilter(AuthService authService, SessionAuthService sessionAuthService) {
        this.authService = authService;
        this.sessionAuthService = sessionAuthService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String ip = getClientIp(request);
        String connectionId = generateConnectionId(request);

        log.info("####\nProcessing request for path: {}, connectionId: {}", path, connectionId);
        request.getQueryParams().forEach((key, value) -> log.info("Param: {}, {}", key, value));
        log.info("####");

        // 提取认证信息 (key 或 sessionId)
        String authKey = extractAuthKey(request);
        String sessionId = extractSessionId(request);

        log.debug("Extracted auth info - key: {}, sessionId: {}",
                maskKey(authKey), sessionId);

        // 确定使用哪种鉴权方式
        return determineAuthMethod(exchange, path, ip, authKey, sessionId)
                .flatMap(authResult -> {
                    if (authResult.isValid()) {
                        log.info("Authentication successful for connectionId: {}, method: {}",
                                connectionId, authResult.getAuthMethod());

                        // 将认证信息传递到下游
                        exchange.getAttributes().put("authKey", authResult.getAuthKey());
                        exchange.getAttributes().put("authMethod", authResult.getAuthMethod());

                        return chain.filter(exchange);
                    } else {
                        log.warn("Authentication failed for connectionId: {}, method: {}, reason: {}",
                                connectionId, authResult.getAuthMethod(), authResult.getFailureReason());
                        return handleUnauthorized(exchange, authResult.getFailureReason());
                    }
                })
                .onErrorResume(throwable -> {
                    log.error("Authentication error for connectionId: {}", connectionId, throwable);
                    return handleUnauthorized(exchange, "鉴权服务失败");
                });
    }

    /**
     * 确定使用哪种鉴权方式
     */
    private Mono<AuthResult> determineAuthMethod(ServerWebExchange exchange, String path, String ip,
                                                 String authKey, String sessionId) {

        // 1. 检查路径白名单
        if (authService.isWhitelistedPath(path)) {
            log.debug("Path {} is whitelisted, allowing access", path);
            return Mono.just(AuthResult.success("WHITELIST", null, sessionId));
        }

        // 2. 检查IP白名单（如果启用）
        if (!authService.isAllowedIp(ip)) {
            log.warn("IP {} is not in whitelist", ip);
            return Mono.just(AuthResult.failure("IP_WHITELIST", "IP不在白名单中"));
        }

        // 3. 如果有authKey，使用key鉴权
        if (authKey != null && !authKey.trim().isEmpty()) {
            return authService.validateAuthKey(authKey)
                    .map(isValid -> {
                        if (isValid) {
                            return AuthResult.success("AUTH_KEY", authKey, sessionId);
                        } else {
                            return AuthResult.failure("AUTH_KEY", "认证key无效");
                        }
                    });
        }

        // 4. 如果有sessionId，使用session鉴权
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            return sessionAuthService.validateSessionId(sessionId)
                    .map(authKey_from_session -> {
                        if (authKey_from_session != null) {
                            return AuthResult.success("SESSION_ID", authKey_from_session, sessionId);
                        } else {
                            return AuthResult.failure("SESSION_ID", "会话无效或已过期");
                        }
                    });
        }

        // 5. 既没有key也没有sessionId
        return Mono.just(AuthResult.failure("NO_AUTH", "缺少认证信息"));
    }

    /**
     * 提取sessionId
     */
    private String extractSessionId(ServerHttpRequest request) {
        // 优先从query参数获取
        String sessionId = request.getQueryParams().getFirst("sessionId");
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            return sessionId.trim();
        }

        // 从Header获取
        return request.getHeaders().getFirst("X-Session-Id");
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json");

        String errorResponse = String.format("{\"error\": \"%s\", \"code\": 403}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
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

    /**
     * 认证结果封装类
     */
    @Getter
    public static class AuthResult {
        private final boolean valid;
        private final String authMethod;
        private final String authKey;
        private final String sessionId;
        private final String failureReason;

        private AuthResult(boolean valid, String authMethod, String authKey,
                           String sessionId, String failureReason) {
            this.valid = valid;
            this.authMethod = authMethod;
            this.authKey = authKey;
            this.sessionId = sessionId;
            this.failureReason = failureReason;
        }

        public static AuthResult success(String authMethod, String authKey, String sessionId) {
            return new AuthResult(true, authMethod, authKey, sessionId, null);
        }

        public static AuthResult failure(String authMethod, String failureReason) {
            return new AuthResult(false, authMethod, null, null, failureReason);
        }

    }
}