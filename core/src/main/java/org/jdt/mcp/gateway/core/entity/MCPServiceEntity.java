package org.jdt.mcp.gateway.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPServiceEntity {
    private Long id;
    private String serviceId;
    private String name;
    private String description;
    private String endpoint;
    private ServiceStatus status;
    private Integer maxQps;
    private String healthCheckUrl;
    private String documentation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
