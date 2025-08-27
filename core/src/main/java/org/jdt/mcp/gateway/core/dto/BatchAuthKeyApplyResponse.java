package org.jdt.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchAuthKeyApplyResponse {
    // 成功申请的密钥列表
    private List<AuthKeyResponse> successKeys;

    // 失败的服务ID及原因
    private List<FailedService> failedServices;

    // 跳过的服务ID（已有有效密钥）
    private List<String> skippedServices;

    // 统计信息
    private Integer totalRequested;
    private Integer successCount;
    private Integer failedCount;
    private Integer skippedCount;

    @Data
    @Builder
    public static class FailedService {
        private String serviceId;
        private String reason;
    }

}