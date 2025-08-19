package org.jdt.mcp.gateway.demo.ctl;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.demo.service.ChatService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

@Slf4j
@Data
@RequiredArgsConstructor
@RequestMapping("mcp")
public class ChatController {
    private ChatService chatService;

    @RequestMapping("/chat")
    public Flux<String> chat(@RequestParam String prompt){
        log.info("User: {}",prompt);
        return chatService.mcpChat(prompt);
    }
}
