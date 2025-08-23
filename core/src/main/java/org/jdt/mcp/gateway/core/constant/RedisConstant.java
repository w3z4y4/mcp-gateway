package org.jdt.mcp.gateway.core.constant;

/**
 * Redis缓存常量定义
 */
public class RedisConstant {

    // 认证Key缓存前缀
    public static final String AUTH_KEY_PREFIX = "auth:key:";

    // 认证Key状态缓存前缀（用于缓存无效key）
    public static final String AUTH_KEY_STATUS_PREFIX = "auth:status:";

    // 服务缓存前缀
    public static final String SERVICE_CACHE_KEY_PREFIX = "service:cache:";

    // 活跃服务集合Key
    public static final String ACTIVE_SERVICES_SET_KEY = "service:active:set";

    // 用户集合前缀（用于统计唯一用户）
    public static final String USER_SET_KEY_PREFIX = "stats:users:";

    // 服务统计前缀
    public static final String SERVICE_STATS_PREFIX = "stats:service:";

    private RedisConstant() {
        // 工具类，禁止实例化
    }
}