package org.jdt.mcp.gateway.atuh.config;

import lombok.Data;
import org.jdt.mcp.gateway.core.entity.AuthType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "jdt.mcp.auth")
public class AuthConfiguration {
    private boolean enabled = true;
    /**
     * 静态认证key列表
     */
    private Set<String> validKeys = Set.of("admin-key-jdt");

    /**
     * 白名单路径，这些路径不需要认证
     */
    private List<String> whitelist = List.of("/health", "/actuator/**");

    /**
     * 认证失败时的响应消息
     */
    private String unauthorizedMessage = "Authentication required";

    /**
     * 是否启用IP白名单
     */
    private boolean enableIpWhitelist = false;

    /**
     * IP白名单
     */
    private Set<String> allowedIps = Set.of("127.0.0.1", "::1");

    private AuthType authType= AuthType.db;

}
