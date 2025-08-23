package org.jdt.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceConfigInfo {
    private String serviceId;
    private String serviceName;
    private String endpoint;
    private String authKey;
    private Integer maxQps;
    private Boolean isActive;
    private String description;
}
