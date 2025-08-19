package org.jdt.mcp.gateway.demo.conf;

public class AuthKeyContext {
    private static final ThreadLocal<String> authKeyHolder = new ThreadLocal<>();

    public static void setAuthKey(String key) {
        authKeyHolder.set(key);
    }

    public static String getAuthKey() {
        return authKeyHolder.get();
    }

    public static void clear() {
        authKeyHolder.remove();
    }

    public static boolean hasAuthKey() {
        return authKeyHolder.get() != null;
    }
}
