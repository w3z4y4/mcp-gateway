package org.jdt.mcp.gateway.demo.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("org.jdt.demo")
public class ApiProperties {
    private String URL= "http://localhost:8088/demo";
}
