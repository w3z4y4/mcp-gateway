package org.jdt.mcp.gateway.demo.service;

import reactor.core.publisher.Flux;

public interface ChatService {
    Flux<String> mcpChat(String prompt);
}
