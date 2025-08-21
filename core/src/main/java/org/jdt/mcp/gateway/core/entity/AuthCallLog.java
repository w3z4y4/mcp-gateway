package org.jdt.mcp.gateway.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthCallLog {
    private Long id;
    private String userId;
    private String serviceId;
    private Long authKeyId;
    private String requestPath;
    private String requestMethod;
    private String clientIp;
    private String userAgent;
    private Integer statusCode;
    private Integer responseTimeMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
