package org.jdt.mcp.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ConfigGenerateRequest {
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    @NotEmpty(message = "Service IDs cannot be empty")
    private List<String> serviceIds;

    //todo 是否从service中获取
    private String baseUrl = "http://localhost:8089"; // 网关基础URL

    private Boolean toolCallbackEnable = true; // 是否启用工具回调

    private Integer timeout = 60; // 超时时间（秒）

    private Boolean autoApprove = false; // 是否自动批准
}