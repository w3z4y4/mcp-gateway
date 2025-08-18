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
public class AuthKeyEntity {
    private Long id;
    private String keyHash;
    private String userId;
    private String MCPServiceId;
    private LocalDateTime expiresAt;
    @Builder.Default
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
