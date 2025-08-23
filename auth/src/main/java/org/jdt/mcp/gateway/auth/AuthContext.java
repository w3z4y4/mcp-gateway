package org.jdt.mcp.gateway.auth;

import lombok.extern.slf4j.Slf4j;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

@Slf4j
public class AuthContext {

    /**
     * 从Reactor Context中获取认证key
     */
    public static String getAuthKey(ContextView context) {
        return context.getOrDefault(AuthConstants.AUTH_KEY, null);
    }

    /**
     * 从Reactor Context中获取连接ID
     */
    public static String getConnectionId(ContextView context) {
        return context.getOrDefault(AuthConstants.CONNECTION_ID, null);
    }

    /**
     * 检查是否已认证
     */
    public static boolean isAuthenticated(ContextView context) {
        String authKey = getAuthKey(context);
        return authKey != null && !authKey.trim().isEmpty();
    }

    /**
     * 创建包含认证信息的Context
     */
    public static Context createAuthContext(String authKey, String connectionId) {
        return Context.of(
                AuthConstants.AUTH_KEY, authKey,
                AuthConstants.CONNECTION_ID, connectionId
        );
    }

    /**
     * 在其他组件中使用的示例方法
     */
    public static void logAuthInfo(ContextView context) {
        String authKey = getAuthKey(context);
        String connectionId = getConnectionId(context);
        log.info("Auth context - Key: {}, Connection: {}",
                authKey != null ? "***" + authKey.substring(Math.max(0, authKey.length() - 4)) : null,
                connectionId);
    }
}
