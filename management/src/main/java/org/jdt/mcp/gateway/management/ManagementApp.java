package org.jdt.mcp.gateway.management;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ComponentScan("org.jdt.mcp.gateway")
@MapperScan("org.jdt.mcp.gateway.mapper")
@EnableTransactionManagement
public class ManagementApp {
    public static void main(String[] args) {
        SpringApplication.run(ManagementApp.class);
    }
}
