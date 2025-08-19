package org.jdt.mcp.gateway.demo.config;

import io.modelcontextprotocol.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class McpConfig {

    private final AsyncMcpToolCallbackProvider toolCallbackProvider;
    public McpConfig(AsyncMcpToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }
    @Bean
    public ChatClient init(OpenAiChatModel openAiChatModel){
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        // 或使用mcpToolProvider
        return ChatClient.builder(openAiChatModel)
                .defaultToolCallbacks(toolCallbacks) // 或使用mcpToolProvider
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
