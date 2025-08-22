package org.jdt.mcp.gateway.proxy.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.atuh.AuthContextHelper;
import org.jdt.mcp.gateway.core.entity.ServiceStatisticsEntity;
import org.jdt.mcp.gateway.mapper.ServiceStatisticsMapper;
import org.jdt.mcp.gateway.proxy.config.ProxyConfiguration;
import org.jdt.mcp.gateway.proxy.service.StatisticsService;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class RedisStatisticsServiceImpl implements StatisticsService {

    private static final String STATS_KEY_PREFIX = "mcp:stats:";
    private static final String DAILY_STATS_KEY_PREFIX = "mcp:daily:";
    private static final String USER_SET_KEY_PREFIX = "mcp:users:";
    private static final Duration CACHE_EXPIRE = Duration.ofHours(25); // 25小时过期

    private final ProxyConfiguration proxyConfig;
    private final AuthContextHelper authContextHelper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ServiceStatisticsMapper statisticsMapper;
    private final ObjectMapper objectMapper;
    private final org.jdt.mcp.gateway.mapper.AuthKeyMapper authKeyMapper;

    public RedisStatisticsServiceImpl(ProxyConfiguration proxyConfig,
                                      AuthContextHelper authContextHelper,
                                      ReactiveStringRedisTemplate redisTemplate,
                                      ServiceStatisticsMapper statisticsMapper,
                                      ObjectMapper objectMapper,
                                      org.jdt.mcp.gateway.mapper.AuthKeyMapper authKeyMapper) {
        this.proxyConfig = proxyConfig;
        this.authContextHelper = authContextHelper;
        this.redisTemplate = redisTemplate;
        this.statisticsMapper = statisticsMapper;
        this.objectMapper = objectMapper;
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
                // 获取认证信息
                AuthContextHelper.AuthInfo authInfo = authContextHelper.getAuthInfoFromExchange(exchange);
                String authKey = authInfo != null ? authInfo.authKey() : null;
                String userId = "anonymous";

                // 如果有认证key，从数据库查询用户ID
                if (authKey != null) {
                    try {
                        var authEntity = authKeyMapper.findByKeyHash(authKey);
                        if (authEntity != null) {
                            userId = authEntity.getUserId();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to query user info for authKey: {}", authKey, e);
                    }
                }

                recordRequestAsync(serviceId, userId, statusCode, responseTime.toMillis());
            } catch (Exception e) {
                log.warn("Failed to record statistics for service: {}", serviceId, e);
            }
        }).then();
    }

    /**
     * 异步记录请求统计
     */
    private void recordRequestAsync(String serviceId, String userId, int statusCode, long responseTimeMs) {
        Mono.fromRunnable(() -> {
            String today = LocalDate.now().toString();
            String statsKey = STATS_KEY_PREFIX + serviceId + ":" + today;
            String userSetKey = USER_SET_KEY_PREFIX + serviceId + ":" + today;

            try {
                // 并行执行多个Redis操作
                Mono.when(
                        // 增加总调用数
                        redisTemplate.opsForHash().increment(statsKey, "total_calls", 1),

                        // 增加成功/失败调用数
                        redisTemplate.opsForHash().increment(statsKey,
                                statusCode >= 200 && statusCode < 300 ? "success_calls" : "failed_calls", 1),

                        // 更新响应时间统计
                        updateResponseTimeStats(statsKey, responseTimeMs),

                        // 记录用户
                        redisTemplate.opsForSet().add(userSetKey, userId),

                        // 设置过期时间
                        redisTemplate.expire(statsKey, CACHE_EXPIRE),
                        redisTemplate.expire(userSetKey, CACHE_EXPIRE)
                ).subscribe(
                        result -> log.debug("Statistics recorded for service: {}", serviceId),
                        error -> log.warn("Failed to record statistics in Redis for service: {}", serviceId, error)
                );

            } catch (Exception e) {
                log.warn("Error recording statistics for service: {}", serviceId, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 更新响应时间统计
     */
    private Mono<Void> updateResponseTimeStats(String statsKey, long responseTimeMs) {
        return redisTemplate.opsForHash().get(statsKey, "total_response_time")
                .cast(String.class)
                .defaultIfEmpty("0")
                .zipWith(redisTemplate.opsForHash().get(statsKey, "max_response_time").cast(String.class).defaultIfEmpty("0"))
                .flatMap(tuple -> {
                    long currentTotal = Long.parseLong(tuple.getT1());
                    long currentMax = Long.parseLong(tuple.getT2());

                    Map<String, String> updateMap = new HashMap<>();
                    updateMap.put("total_response_time", String.valueOf(currentTotal + responseTimeMs));

                    if (responseTimeMs > currentMax) {
                        updateMap.put("max_response_time", String.valueOf(responseTimeMs));
                    }

                    return redisTemplate.opsForHash().putAll(statsKey, updateMap);
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

            return new ServiceStats(
                    entity.getTotalCalls(),
                    entity.getSuccessCalls(),
                    entity.getFailedCalls(),
                    entity.getAvgResponseTimeMs(),
                    entity.getMaxResponseTimeMs(),
                    entity.getUniqueUsers(),
                    entity.getUpdatedAt()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<ServiceStats> getRealtimeServiceStats(String serviceId) {
        String today = LocalDate.now().toString();
        String statsKey = STATS_KEY_PREFIX + serviceId + ":" + today;
        String userSetKey = USER_SET_KEY_PREFIX + serviceId + ":" + today;

        return Mono.zip(
                redisTemplate.opsForHash().entries(statsKey).collectMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString()),
                redisTemplate.opsForSet().size(userSetKey)
        ).map(tuple -> {
            Map<String, String> stats = tuple.getT1();
            Long uniqueUsers = tuple.getT2();

            int totalCalls = getIntValue(stats, "total_calls");
            int successCalls = getIntValue(stats, "success_calls");
            int failedCalls = getIntValue(stats, "failed_calls");
            long totalResponseTime = getLongValue(stats, "total_response_time");
            long maxResponseTime = getLongValue(stats, "max_response_time");

            long avgResponseTime = totalCalls > 0 ? totalResponseTime / totalCalls : 0;

            return new ServiceStats(
                    totalCalls,
                    successCalls,
                    failedCalls,
                    avgResponseTime,
                    maxResponseTime,
                    uniqueUsers.intValue(),
                    LocalDateTime.now()
            );
        }).onErrorReturn(ServiceStats.empty());
    }

    @Override
    public Mono<Void> clearStats() {
        String pattern = STATS_KEY_PREFIX + "*";
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then()
                .doOnSuccess(v -> log.info("Statistics cache cleared"));
    }

    @Override
    public Mono<Void> flushStatisticsToDatabase() {
        return redisTemplate.keys(STATS_KEY_PREFIX + "*")
                .flatMap(this::flushSingleServiceStats)
                .then()
                .doOnSuccess(v -> log.info("Statistics flushed to database"))
                .doOnError(e -> log.error("Failed to flush statistics to database", e));
    }

    /**
     * 刷新单个服务的统计数据到数据库
     */
    private Mono<Void> flushSingleServiceStats(String statsKey) {
        return Mono.fromRunnable(() -> {
            try {
                // 解析serviceId和date
                String[] parts = statsKey.replace(STATS_KEY_PREFIX, "").split(":");
                if (parts.length < 2) return;

                String serviceId = parts[0];
                LocalDate dateKey = LocalDate.parse(parts[1]);
                String userSetKey = USER_SET_KEY_PREFIX + serviceId + ":" + parts[1];

                // 从Redis获取统计数据
                Mono.zip(
                        redisTemplate.opsForHash().entries(statsKey).collectMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString()),
                        redisTemplate.opsForSet().size(userSetKey)
                ).subscribe(tuple -> {
                    Map<String, String> stats = tuple.getT1();
                    Long uniqueUsers = tuple.getT2();

                    int totalCalls = getIntValue(stats, "total_calls");
                    if (totalCalls == 0) return; // 没有调用数据，跳过

                    int successCalls = getIntValue(stats, "success_calls");
                    int failedCalls = getIntValue(stats, "failed_calls");
                    long totalResponseTime = getLongValue(stats, "total_response_time");
                    long maxResponseTime = getLongValue(stats, "max_response_time");

                    int avgResponseTime = totalCalls > 0 ? (int) (totalResponseTime / totalCalls) : 0;

                    // 创建实体并保存到数据库
                    ServiceStatisticsEntity entity = new ServiceStatisticsEntity();
                    entity.setServiceId(serviceId);
                    entity.setDateKey(dateKey);
                    entity.setTotalCalls(totalCalls);
                    entity.setSuccessCalls(successCalls);
                    entity.setFailedCalls(failedCalls);
                    entity.setAvgResponseTimeMs(avgResponseTime);
                    entity.setMaxResponseTimeMs((int) maxResponseTime);
                    entity.setUniqueUsers(uniqueUsers.intValue());

                    try {
                        statisticsMapper.insertOrUpdate(entity);
                        log.debug("Flushed statistics to DB for service: {} on {}", serviceId, dateKey);
                    } catch (Exception e) {
                        log.error("Failed to save statistics to database for service: {}", serviceId, e);
                    }
                });

            } catch (Exception e) {
                log.warn("Error flushing statistics for key: {}", statsKey, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private int getIntValue(Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    private long getLongValue(Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }
}