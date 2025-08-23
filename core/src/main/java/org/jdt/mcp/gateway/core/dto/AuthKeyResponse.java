package org.jdt.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuthKeyResponse {
    private Long id;
    private String keyHash; // 返回给前端的key（生产环境可能需要脱敏）
    private String userId;
    private String serviceId;
    private String serviceName; // 关联的服务名称
    private LocalDateTime expiresAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private String remarks;
}
