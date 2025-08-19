package org.jdt.mcp.gateway.demo.entity;

import lombok.Data;

@Data
public class Worker {
    private String name;
    private String job;

    public Worker(Employee employee){
        name = employee.getName();
        job = "码农魂修特长生";
    }
}
