package org.jdt.mcp.gateway.assembly;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "org.jdt.mcp.gateway")
@MapperScan("org.jdt.mcp.gateway.management.mapper")
@EnableTransactionManagement
public class GatewayAssemblyApp {
    public static void main(String[] args) {
        SpringApplication.run(GatewayAssemblyApp.class);
    }
}
