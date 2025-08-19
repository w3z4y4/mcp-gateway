package org.jdt.mcp.gateway.demo.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Data
@RequiredArgsConstructor
@Service
public class ChatServiceImpl implements ChatService{
    private ChatClient client;

    @Override
    public Flux<String> mcpChat(String prompt) {
        return client.prompt(prompt).stream().content();
    }
}
