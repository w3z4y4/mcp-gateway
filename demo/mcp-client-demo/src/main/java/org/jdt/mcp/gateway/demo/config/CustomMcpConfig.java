package org.jdt.mcp.gateway.demo.config;

import io.modelcontextprotocol.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class CustomMcpConfig implements McpAsyncClientCustomizer {
    @Override
    public void customize(String name, McpClient.AsyncSpec spec) {
        log.info("McpAsyncClientCustomizer is {}",name);
        spec.requestTimeout(Duration.ofSeconds(30));
    }
}
