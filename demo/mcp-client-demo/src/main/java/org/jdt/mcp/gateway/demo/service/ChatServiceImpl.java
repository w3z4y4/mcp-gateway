package org.jdt.mcp.gateway.demo.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


@Service
public class ChatServiceImpl implements ChatService{
    private final ChatClient client;

    public ChatServiceImpl(ChatClient client){
        this.client=client;
    }
    @Override
    public Flux<String> mcpChat(String prompt) {
        return client.prompt(prompt).stream().content();
    }
}
