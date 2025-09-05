package org.jdt.mcp.gateway.demo.service;

import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {
    Flux<String> mcpChat(String prompt);
    Flux<String> chatWithService(String prompt,String userId, List<String> serviceIds);
}
