package org.jdt.mcp.gateway.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;

@Data
public class MCPServiceCreateRequest {
    @NotBlank(message = "Service ID cannot be blank")
    private String serviceId;

    @NotBlank(message = "Service name cannot be blank")
    private String name;

    private String description;

    @NotBlank(message = "Endpoint cannot be blank")
    private String endpoint;

    @NotNull(message = "Status cannot be null")
    private ServiceStatus status = ServiceStatus.ACTIVE;

    @Positive(message = "Max QPS must be positive")
    private Integer maxQps = 100;

    private String healthCheckUrl;

    private String documentation;
}
