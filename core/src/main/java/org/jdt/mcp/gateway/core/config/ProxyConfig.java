package org.jdt.mcp.gateway.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jdt.mcp.proxy")
public class ProxyConfig {

    // mcp-proxy基础URL
    private String baseUrl = "http://localhost:8080";

    /**
     * 代理超时时间
     */
    private Duration timeout = Duration.ofSeconds(300);

    /**
     * 最大内存大小（用于处理请求体）
     */
    private long maxInMemorySize = 256 * 1024; // 256KB

    /**
     * 连接超时
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 读超时
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    /**
     * 是否启用统计
     */
    private boolean enableStatistics = true;

    /**
     * 是否启用请求日志
     */
    private boolean enableRequestLogging = true;
}