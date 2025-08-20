package org.jdt.mcp.gateway.atuh.tool;

import org.springframework.http.server.reactive.ServerHttpRequest;

public class AuthReqTool {
    /**
     * 取鉴权key
     * @param request 原始请求
     * @return 从param中获取 -> 从Authorization Header获取 -> 从X-Auth-Key Header获取
     */
    public static String extractAuthKey(ServerHttpRequest request) {
        // 优先从query参数获取
        String authKey = request.getQueryParams().getFirst("key");
        if (authKey != null && !authKey.trim().isEmpty()) {
            return authKey.trim();
        }

        // 从Header获取
        authKey = request.getHeaders().getFirst("Authorization");
        if (authKey != null && authKey.startsWith("Bearer ")) {
            return authKey.substring(7);
        }

        // 从X-Auth-Key header获取
        return request.getHeaders().getFirst("X-Auth-Key");
    }

    public static String generateConnectionId(ServerHttpRequest request) {
        String clientIp = getClientIp(request);
        return clientIp + "_" + System.currentTimeMillis();
    }

    public static String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}
