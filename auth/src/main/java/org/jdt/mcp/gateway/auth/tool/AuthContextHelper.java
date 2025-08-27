package org.jdt.mcp.gateway.auth.tool;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.constant.AuthConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthContextHelper {

    /**
     * 从ServerWebExchange获取认证信息（推荐方式）
     */
    public AuthInfo getAuthInfoFromExchange(ServerWebExchange exchange) {
        String authKey = exchange.getAttribute(AuthConstants.AUTH_KEY);
        String connectionId = exchange.getAttribute(AuthConstants.CONNECTION_ID);
        return new AuthInfo(authKey, connectionId);
    }

    /**
     * 从Reactor Context获取认证信息
     */
    public AuthInfo getAuthInfoSync() {
        try {
            return Mono.deferContextual(contextView -> {
                String authKey = AuthContext.getAuthKey(contextView);
                String connectionId = AuthContext.getConnectionId(contextView);
                return Mono.just(new AuthInfo(authKey, connectionId));
            }).block();
        } catch (Exception e) {
            log.warn("Failed to get auth info from context: {}", e.getMessage());
            return null;
        }
    }

    // 认证信息封装类
    public record AuthInfo(String authKey, String connectionId) {
        private String maskAuthKey(String authKey) {
            if (authKey == null || authKey.length() < 4) {
                return "***";
            }
            return "***" + authKey.substring(authKey.length() - 4);
        }
    }
}
