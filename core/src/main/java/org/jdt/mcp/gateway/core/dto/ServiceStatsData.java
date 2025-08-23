package org.jdt.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ServiceStatsData {
    private int totalCalls;
    private int successCalls;
    private int failedCalls;
    private long avgResponseTimeMs;
    private long maxResponseTimeMs;
    private int uniqueUsers;
    private LocalDateTime lastUpdateTime;

    public static ServiceStatsData empty() {
        return ServiceStatsData.builder()
                .totalCalls(0)
                .successCalls(0)
                .failedCalls(0)
                .avgResponseTimeMs(0)
                .maxResponseTimeMs(0)
                .uniqueUsers(0)
                .lastUpdateTime(null)
                .build();
    }

    public double getSuccessRate() {
        return totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0;
    }
}
