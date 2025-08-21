package org.jdt.mcp.gateway.proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.atuh.AuthContextHelper;
import org.jdt.mcp.gateway.proxy.config.ProxyConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class StatisticsService {

    // todo 未实际记录日志

    private final ProxyConfiguration proxyConfig;
    private final AuthContextHelper authContextHelper;

    // 内存统计数据（简单实现，生产环境建议使用Redis）
    private final ConcurrentMap<String, ServiceStats> serviceStatsMap = new ConcurrentHashMap<>();

    public StatisticsService(ProxyConfiguration proxyConfig, AuthContextHelper authContextHelper) {
        this.proxyConfig = proxyConfig;
        this.authContextHelper = authContextHelper;
    }

    /**
     * 记录请求统计
     */
    public Mono<Void> recordRequest(ServerWebExchange exchange, String serviceId,
                                    int statusCode, Duration responseTime) {
        if (!proxyConfig.isEnableStatistics()) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                // 获取用户信息
                AuthContextHelper.AuthInfo authInfo = authContextHelper.getAuthInfoFromExchange(exchange);
                String userId = authInfo != null ? authInfo.authKey() : "anonymous";

                // 异步记录统计
                CompletableFuture.runAsync(() -> {
                    recordServiceCall(serviceId, userId, statusCode, responseTime.toMillis());
                }, Schedulers.boundedElastic().createWorker()::schedule);

            } catch (Exception e) {
                log.warn("Failed to record statistics for service: {}", serviceId, e);
            }
        }).then();
    }

    /**
     * 记录服务调用
     */
    private void recordServiceCall(String serviceId, String userId, int statusCode, long responseTimeMs) {
        ServiceStats stats = serviceStatsMap.computeIfAbsent(serviceId, k -> new ServiceStats());

        stats.totalCalls.incrementAndGet();
        stats.totalResponseTime.addAndGet(responseTimeMs);
        stats.maxResponseTime.updateAndGet(current -> Math.max(current, responseTimeMs));

        if (statusCode >= 200 && statusCode < 300) {
            stats.successCalls.incrementAndGet();
        } else {
            stats.failedCalls.incrementAndGet();
        }

        stats.lastCallTime = LocalDateTime.now();

        log.debug("Recorded stats for service {}: total={}, success={}, failed={}, avgTime={}ms",
                serviceId, stats.totalCalls.get(), stats.successCalls.get(),
                stats.failedCalls.get(), stats.getAverageResponseTime());
    }

    /**
     * 获取服务统计信息
     */
    public ServiceStats getServiceStats(String serviceId) {
        return serviceStatsMap.getOrDefault(serviceId, new ServiceStats());
    }

    /**
     * 清理统计数据（可定期调用）
     */
    public void clearStats() {
        serviceStatsMap.clear();
        log.info("Statistics data cleared");
    }

    /**
     * 服务统计数据类
     */
    public static class ServiceStats {
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger successCalls = new AtomicInteger(0);
        private final AtomicInteger failedCalls = new AtomicInteger(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        private volatile LocalDateTime lastCallTime;

        public int getTotalCalls() { return totalCalls.get(); }
        public int getSuccessCalls() { return successCalls.get(); }
        public int getFailedCalls() { return failedCalls.get(); }
        public long getMaxResponseTime() { return maxResponseTime.get(); }
        public LocalDateTime getLastCallTime() { return lastCallTime; }

        public double getAverageResponseTime() {
            int total = totalCalls.get();
            return total > 0 ? (double) totalResponseTime.get() / total : 0;
        }

        public double getSuccessRate() {
            int total = totalCalls.get();
            return total > 0 ? (double) successCalls.get() / total * 100 : 0;
        }
    }
}