package org.jdt.mcp.gateway.demo.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.demo.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {
    private final ChatClient client;
    private final AsyncMcpToolCallbackProvider toolCallbackProvider;

    public ChatServiceImpl(ChatClient client
            ,AsyncMcpToolCallbackProvider toolCallbackProvider){
        this.client=client;
        this.toolCallbackProvider = toolCallbackProvider;
    }
    @Override
    public Flux<String> mcpChat(String prompt) {
        return client.prompt(prompt).stream().content();
    }

    @Override
    public Flux<String> chatWithService(String prompt, String userId, List<String> serviceIds) {
        List<ToolCallback> callbacks= new ArrayList<>();
        List.of(toolCallbackProvider.getToolCallbacks()).forEach(toolCallback -> {
            if (serviceIds.contains(toolCallback.getToolDefinition().name())){
                callbacks.add(toolCallback);
            }
        });
        return client.prompt(prompt).toolCallbacks(callbacks).stream().content();
    }
    @PostConstruct
    private void init(){
        log.info("######");
        List.of(toolCallbackProvider.getToolCallbacks()).forEach(toolCallback -> {
            log.info(toolCallback.getToolMetadata().toString());
            log.info(toolCallback.getToolDefinition().name());
        });
        log.info("######");
    }

}
