package org.jdt.mcp.gateway.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AuthKeyApplyRequest {
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    @NotBlank(message = "Service ID cannot be blank")
    private String serviceId;

    @Positive(message = "Expire hours must be positive")
    private Long expireHours; // null表示永不过期

    private String remarks; // 申请备注
}
