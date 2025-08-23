package org.jdt.mcp.gateway.proxy.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.auth.AuthContextHelper;
import org.jdt.mcp.gateway.core.dto.ServiceStatsData;
import org.jdt.mcp.gateway.core.entity.ServiceStatisticsEntity;
import org.jdt.mcp.gateway.mapper.AuthKeyMapper;
import org.jdt.mcp.gateway.mapper.ServiceStatisticsMapper;
import org.jdt.mcp.gateway.proxy.config.ProxyConfiguration;
import org.jdt.mcp.gateway.proxy.service.StatisticsService;
import org.jdt.mcp.gateway.service.RedisStatsCacheService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
public class RedisStatisticsServiceImpl implements StatisticsService {

    private final ProxyConfiguration proxyConfig;
    private final AuthContextHelper authContextHelper;
    private final RedisStatsCacheService redisStatsService;
    private final ServiceStatisticsMapper statisticsMapper;
    private final AuthKeyMapper authKeyMapper;

    public RedisStatisticsServiceImpl(ProxyConfiguration proxyConfig,
                                      AuthContextHelper authContextHelper,
                                      RedisStatsCacheService redisStatsService,
                                      ServiceStatisticsMapper statisticsMapper,
                                      AuthKeyMapper authKeyMapper) {
        this.proxyConfig = proxyConfig;
        this.authContextHelper = authContextHelper;
        this.redisStatsService = redisStatsService;
        this.statisticsMapper = statisticsMapper;
        this.authKeyMapper = authKeyMapper;
    }

    @Override
    public Mono<Void> recordRequest(ServerWebExchange exchange, String serviceId,
                                    int statusCode, Duration responseTime) {
        if (!proxyConfig.isEnableStatistics()) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                // 提取用户信息
                String userId = extractUserId(exchange);

                // 异步记录统计
                redisStatsService.recordRequestStats(serviceId, userId, statusCode, responseTime.toMillis())
                        .doOnSuccess(v -> log.debug("Statistics recorded for service: {}", serviceId))
                        .doOnError(error -> log.warn("Failed to record statistics for service: {}", serviceId, error))
                        .subscribe();

            } catch (Exception e) {
                log.warn("Failed to process statistics recording for service: {}", serviceId, e);
            }
        }).then();
    }

    @Override
    public Mono<ServiceStats> getServiceStats(String serviceId) {
        // 从数据库获取历史统计数据
        return Mono.fromCallable(() -> {
                    ServiceStatisticsEntity entity = statisticsMapper.findByServiceIdAndDate(serviceId, LocalDate.now());
                    if (entity == null) {
                        return ServiceStats.empty();
                    }

                    return convertToServiceStats(entity);
                }).subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> log.warn("Error getting service stats from database: {}", serviceId, error));
    }

    @Override
    public Mono<ServiceStats> getRealtimeServiceStats(String serviceId) {
        return redisStatsService.getRealtimeServiceStats(serviceId)
                .map(this::convertToServiceStats)
                .onErrorReturn(ServiceStats.empty())
                .doOnError(error -> log.warn("Error getting realtime service stats: {}", serviceId, error));
    }

    @Override
    public Mono<Void> clearStats() {
        return redisStatsService.clearStats()
                .doOnSuccess(v -> log.info("Statistics cache cleared"))
                .doOnError(error -> log.error("Failed to clear statistics cache", error));
    }

    @Override
    public Mono<Void> flushStatisticsToDatabase() {
        return redisStatsService.getAllStatisticsKeys()
                .flatMap(this::flushSingleServiceStats)
                .then()
                .doOnSuccess(v -> log.info("Statistics flushed to database"))
                .doOnError(e -> log.error("Failed to flush statistics to database", e));
    }

    /**
     * 提取用户ID
     */
    private String extractUserId(ServerWebExchange exchange) {
        try {
            AuthContextHelper.AuthInfo authInfo = authContextHelper.getAuthInfoFromExchange(exchange);
            String authKey = authInfo != null ? authInfo.authKey() : null;

            if (authKey != null) {
                var authEntity = authKeyMapper.findByKeyHash(authKey);
                if (authEntity != null) {
                    return authEntity.getUserId();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from exchange", e);
        }

        return "anonymous";
    }

    /**
     * 转换为ServiceStats对象
     */
    private ServiceStats convertToServiceStats(ServiceStatsData data) {
        return new ServiceStats(
                data.getTotalCalls(),
                data.getSuccessCalls(),
                data.getFailedCalls(),
                data.getAvgResponseTimeMs(),
                data.getMaxResponseTimeMs(),
                data.getUniqueUsers(),
                data.getLastUpdateTime()
        );
    }

    /**
     * 转换为ServiceStats对象 (从数据库实体)
     */
    private ServiceStats convertToServiceStats(ServiceStatisticsEntity entity) {
        return new ServiceStats(
                entity.getTotalCalls(),
                entity.getSuccessCalls(),
                entity.getFailedCalls(),
                entity.getAvgResponseTimeMs(),
                entity.getMaxResponseTimeMs(),
                entity.getUniqueUsers(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 刷新单个服务的统计数据到数据库
     */
    private Mono<Void> flushSingleServiceStats(String statsKey) {
        return Mono.fromRunnable(() -> {
            try {
                // 解析serviceId和date
                String[] parts = statsKey.replace("stats:service:", "").split(":");
                if (parts.length < 2) return;

                String serviceId = parts[0];
                LocalDate dateKey = LocalDate.parse(parts[1]);

                // 从Redis获取统计数据并保存到数据库
                redisStatsService.getServiceStatsFromCache(serviceId, dateKey)
                        .zipWith(redisStatsService.getUniqueUsersCount(serviceId, dateKey))
                        .subscribe(tuple -> {
                            try {
                                var stats = tuple.getT1();
                                Long uniqueUsers = tuple.getT2();

                                int totalCalls = getIntValue(stats, "total_calls");
                                if (totalCalls == 0) return; // 没有调用数据，跳过

                                ServiceStatisticsEntity entity = buildStatisticsEntity(
                                        serviceId, dateKey, stats, uniqueUsers);

                                statisticsMapper.insertOrUpdate(entity);
                                log.debug("Flushed statistics to DB for service: {} on {}", serviceId, dateKey);
                            } catch (Exception e) {
                                log.error("Failed to save statistics to database for service: {}", serviceId, e);
                            }
                        }, error -> log.warn("Error flushing statistics for key: {}", statsKey, error));

            } catch (Exception e) {
                log.warn("Error processing statistics key: {}", statsKey, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 构建统计实体
     */
    private ServiceStatisticsEntity buildStatisticsEntity(String serviceId, LocalDate dateKey,
                                                          java.util.Map<String, String> stats, Long uniqueUsers) {
        int totalCalls = getIntValue(stats, "total_calls");
        int successCalls = getIntValue(stats, "success_calls");
        int failedCalls = getIntValue(stats, "failed_calls");
        long totalResponseTime = getLongValue(stats, "total_response_time");
        long maxResponseTime = getLongValue(stats, "max_response_time");

        int avgResponseTime = totalCalls > 0 ? (int) (totalResponseTime / totalCalls) : 0;

        ServiceStatisticsEntity entity = new ServiceStatisticsEntity();
        entity.setServiceId(serviceId);
        entity.setDateKey(dateKey);
        entity.setTotalCalls(totalCalls);
        entity.setSuccessCalls(successCalls);
        entity.setFailedCalls(failedCalls);
        entity.setAvgResponseTimeMs(avgResponseTime);
        entity.setMaxResponseTimeMs((int) maxResponseTime);
        entity.setUniqueUsers(uniqueUsers.intValue());

        return entity;
    }

    /**
     * 获取缓存统计信息 (业务层方法)
     */
    public Mono<ServiceStatsData> getCachedServiceStats(String serviceId) {
        return redisStatsService.getRealtimeServiceStats(serviceId)
                .doOnNext(stats -> log.debug("Retrieved cached stats for service: {}", serviceId))
                .doOnError(error -> log.warn("Error getting cached stats for service: {}", serviceId, error));
    }

    /**
     * 检查是否有统计缓存
     */
    public Mono<Boolean> hasStatsCache(String serviceId) {
        return redisStatsService.hasStatsCache(serviceId, LocalDate.now());
    }

    /**
     * 删除服务统计缓存
     */
    public Mono<Void> deleteServiceStatsCache(String serviceId) {
        return redisStatsService.deleteServiceStats(serviceId, LocalDate.now())
                .doOnSuccess(v -> log.info("Deleted stats cache for service: {}", serviceId))
                .doOnError(error -> log.warn("Error deleting stats cache for service: {}", serviceId, error));
    }

    private int getIntValue(java.util.Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    private long getLongValue(java.util.Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }
}