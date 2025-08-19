package org.jdt.mcp.gateway.demo.ctl;

import lombok.Data;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("demo")
public class DemoController {
    @RequestMapping("/flux")
    public Flux<String> fluxDemo(@RequestBody Employee employee){
        return Flux.just("hello "+employee.getName());
    }
    @RequestMapping("/simple")
    public String demo(@RequestBody Employee employee){
        return "hello "+employee.getName();
    }

    @Data
    public static class Employee{
        String name;
        String phoneNum;
        String department;
    }
}
