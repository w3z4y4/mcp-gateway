package org.jdt.mcp.gateway.core.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Spring AI MCP Client完整配置
 */
@Data
@Builder
public class MCPClientConfig {
    private SpringConfig spring;

    @Data
    @Builder
    public static class SpringConfig {
        private AIConfig ai;
    }

    @Data
    @Builder
    public static class AIConfig {
        private MCPConfig mcp;
    }

    @Data
    @Builder
    public static class MCPConfig {
        private ClientConfig client;
    }

    @Data
    @Builder
    public static class ClientConfig {
        private ToolCallbackConfig toolcallback;
        private SSEConfig sse;
        private String type; // "async" or "sync"
    }

    @Data
    @Builder
    public static class ToolCallbackConfig {
        private Boolean enable;
    }

    @Data
    @Builder
    public static class SSEConfig {
        private Map<String, ServiceConnectionConfig> connections;
    }

    @Data
    @Builder
    public static class ServiceConnectionConfig {
        private String url;
        private Integer timeout;
        private Boolean disabled;
        private List<String> autoApprove; // 自动批准的工具列表
    }
}
