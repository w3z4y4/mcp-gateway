package org.jdt.mcp.gateway.hr.conf;

import org.jdt.mcp.gateway.hr.service.DemoService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {
    @Bean
    public ToolCallbackProvider toolCallbackProvider(DemoService demoService){
        return MethodToolCallbackProvider.builder().toolObjects(demoService).build();
    }
}
