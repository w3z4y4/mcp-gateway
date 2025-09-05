package org.jdt.mcp.gateway.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("org.jdt.mcp.gateway")
@SpringBootApplication
public class HrMcpServer {
    public static void main(String[] args) {
        SpringApplication.run(HrMcpServer.class);
    }
}
