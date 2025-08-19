package org.jdt.mcp.gateway.demo.ctl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.demo.service.ChatService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/mcp")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService){
        this.chatService = chatService;
    }

    @RequestMapping("/chat")
    public Flux<String> chat(@RequestParam String prompt){
        log.info("User: {}",prompt);
        return chatService.mcpChat(prompt);
    }
    @RequestMapping("/hello")
    public String hi(){
        return "hello";
    }
}
