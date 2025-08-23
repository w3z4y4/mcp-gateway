package org.jdt.mcp.gateway.core.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;

@Data
public class MCPServiceUpdateRequest {
    private String name;
    private String description;
    private String endpoint;
    private ServiceStatus status;

    @Positive(message = "Max QPS must be positive")
    private Integer maxQps;

    private String healthCheckUrl;
    private String documentation;
}