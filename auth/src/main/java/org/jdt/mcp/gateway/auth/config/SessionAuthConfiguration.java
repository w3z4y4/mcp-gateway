package org.jdt.mcp.gateway.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Session认证配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jdt.mcp.auth.session")
public class SessionAuthConfiguration {

    /**
     * 是否启用Session认证功能
     */
    private boolean enabled = true;

    /**
     * 默认Session过期时间（小时）
     */
    private long defaultTtlHours = 2;

    /**
     * 最大Session过期时间（小时）
     */
    private long maxTtlHours = 24;

    /**
     * Session清理任务执行间隔（分钟）
     */
    private long cleanupIntervalMinutes = 30;

    /**
     * 是否自动延长活跃Session的过期时间
     */
    private boolean autoExtendTtl = true;

    /**
     * 自动延长的时间（小时）
     */
    private long autoExtendHours = 1;

    /**
     * Redis key前缀
     */
    private String keyPrefix = "session:auth:";

    /**
     * 是否启用Session访问日志
     */
    private boolean enableAccessLog = true;

    /**
     * 获取默认TTL Duration对象
     */
    public Duration getDefaultTtl() {
        return Duration.ofHours(defaultTtlHours);
    }

    /**
     * 获取最大TTL Duration对象
     */
    public Duration getMaxTtl() {
        return Duration.ofHours(maxTtlHours);
    }

    /**
     * 获取自动延长TTL Duration对象
     */
    public Duration getAutoExtendTtl() {
        return Duration.ofHours(autoExtendHours);
    }

    /**
     * 获取清理间隔Duration对象
     */
    public Duration getCleanupInterval() {
        return Duration.ofMinutes(cleanupIntervalMinutes);
    }

    /**
     * 验证TTL是否在允许范围内
     */
    public boolean isValidTtl(Duration ttl) {
        return ttl != null &&
                !ttl.isNegative() &&
                ttl.compareTo(getMaxTtl()) <= 0;
    }

    /**
     * 获取安全的TTL（确保在允许范围内）
     */
    public Duration getSafeTtl(Duration requestedTtl) {
        if (requestedTtl == null) {
            return getDefaultTtl();
        }

        if (requestedTtl.isNegative()) {
            return getDefaultTtl();
        }

        if (requestedTtl.compareTo(getMaxTtl()) > 0) {
            return getMaxTtl();
        }

        return requestedTtl;
    }
}