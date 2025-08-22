package org.jdt.mcp.gateway.proxy.service;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 统计服务接口
 */
public interface StatisticsService {

    /**
     * 记录请求统计
     * @param exchange Web交换对象
     * @param serviceId 服务ID
     * @param statusCode 状态码
     * @param responseTime 响应时间
     * @return Mono<Void>
     */
    Mono<Void> recordRequest(ServerWebExchange exchange, String serviceId,
                             int statusCode, Duration responseTime);

    /**
     * 获取服务统计信息
     * @param serviceId 服务ID
     * @return 服务统计信息
     */
    Mono<ServiceStats> getServiceStats(String serviceId);

    /**
     * 获取服务实时统计信息（从Redis缓存）
     * @param serviceId 服务ID
     * @return 实时统计信息
     */
    Mono<ServiceStats> getRealtimeServiceStats(String serviceId);

    /**
     * 清理统计数据
     */
    Mono<Void> clearStats();

    /**
     * 批量保存统计数据到数据库（定时任务调用）
     */
    Mono<Void> flushStatisticsToDatabase();

    /**
     * 服务统计数据类
     */
    class ServiceStats {
        private final int totalCalls;
        private final int successCalls;
        private final int failedCalls;
        private final long avgResponseTimeMs;
        private final long maxResponseTimeMs;
        private final int uniqueUsers;
        private final LocalDateTime lastCallTime;

        public ServiceStats(int totalCalls, int successCalls, int failedCalls,
                            long avgResponseTimeMs, long maxResponseTimeMs,
                            int uniqueUsers, LocalDateTime lastCallTime) {
            this.totalCalls = totalCalls;
            this.successCalls = successCalls;
            this.failedCalls = failedCalls;
            this.avgResponseTimeMs = avgResponseTimeMs;
            this.maxResponseTimeMs = maxResponseTimeMs;
            this.uniqueUsers = uniqueUsers;
            this.lastCallTime = lastCallTime;
        }

        // Getters
        public int getTotalCalls() { return totalCalls; }
        public int getSuccessCalls() { return successCalls; }
        public int getFailedCalls() { return failedCalls; }
        public long getAverageResponseTime() { return avgResponseTimeMs; }
        public long getMaxResponseTime() { return maxResponseTimeMs; }
        public int getUniqueUsers() { return uniqueUsers; }
        public LocalDateTime getLastCallTime() { return lastCallTime; }

        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0;
        }

        // 创建空统计对象
        public static ServiceStats empty() {
            return new ServiceStats(0, 0, 0, 0, 0, 0, null);
        }
    }
}