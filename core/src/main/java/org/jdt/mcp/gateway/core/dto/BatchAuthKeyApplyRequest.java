package org.jdt.mcp.gateway.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class BatchAuthKeyApplyRequest {
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    @NotEmpty(message = "Service IDs cannot be empty")
    private List<String> serviceIds;

    @Positive(message = "Expire hours must be positive")
    private Long expireHours; // null表示永不过期

    private String remarks; // 申请备注

    // 是否跳过已存在的密钥（如果为true，对已有密钥的服务会跳过而不报错）
    private Boolean skipExisting = true;

}