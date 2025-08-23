package org.jdt.mcp.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.dto.ServiceStatsData;
import org.jdt.mcp.gateway.service.RedisStatsCacheService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.jdt.mcp.gateway.core.constant.RedisConstant.*;

@Slf4j
@Service
public class RedisStatsCacheServiceImpl implements RedisStatsCacheService {

    private static final Duration CACHE_EXPIRE = Duration.ofHours(25); // 25小时过期

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisStatsCacheServiceImpl(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> recordRequestStats(String serviceId, String userId, int statusCode, long responseTimeMs) {
        String today = LocalDate.now().toString();
        String statsKey = SERVICE_STATS_PREFIX + serviceId + ":" + today;
        String userSetKey = USER_SET_KEY_PREFIX + serviceId + ":" + today;

        return Mono.when(
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
                ).then()
                .doOnSuccess(v -> log.debug("Statistics recorded for service: {}", serviceId))
                .doOnError(error -> log.warn("Failed to record statistics for service: {}", serviceId, error));
    }

    @Override
    public Mono<ServiceStatsData> getRealtimeServiceStats(String serviceId) {
        String today = LocalDate.now().toString();

        return Mono.zip(
                getServiceStatsFromCache(serviceId, LocalDate.now()),
                getUniqueUsersCount(serviceId, LocalDate.now())
        ).map(tuple -> {
            Map<String, String> stats = tuple.getT1();
            Long uniqueUsers = tuple.getT2();

            int totalCalls = getIntValue(stats, "total_calls");
            int successCalls = getIntValue(stats, "success_calls");
            int failedCalls = getIntValue(stats, "failed_calls");
            long totalResponseTime = getLongValue(stats, "total_response_time");
            long maxResponseTime = getLongValue(stats, "max_response_time");

            long avgResponseTime = totalCalls > 0 ? totalResponseTime / totalCalls : 0;

            return ServiceStatsData.builder()
                    .totalCalls(totalCalls)
                    .successCalls(successCalls)
                    .failedCalls(failedCalls)
                    .avgResponseTimeMs(avgResponseTime)
                    .maxResponseTimeMs(maxResponseTime)
                    .uniqueUsers(uniqueUsers.intValue())
                    .lastUpdateTime(LocalDateTime.now())
                    .build();
        }).onErrorReturn(ServiceStatsData.empty());
    }

    @Override
    public Mono<Map<String, String>> getServiceStatsFromCache(String serviceId, LocalDate date) {
        String statsKey = SERVICE_STATS_PREFIX + serviceId + ":" + date.toString();

        return redisTemplate.opsForHash().entries(statsKey)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                )
                .doOnError(error -> log.warn("Failed to get service stats from cache: {}", serviceId, error));
    }

    @Override
    public Mono<Long> getUniqueUsersCount(String serviceId, LocalDate date) {
        String userSetKey = USER_SET_KEY_PREFIX + serviceId + ":" + date.toString();

        return redisTemplate.opsForSet().size(userSetKey)
                .doOnError(error -> log.warn("Failed to get unique users count: {}", serviceId, error));
    }

    @Override
    public Mono<Void> clearStats() {
        String statsPattern = SERVICE_STATS_PREFIX + "*";
        String userPattern = USER_SET_KEY_PREFIX + "*";

        return Mono.when(
                        redisTemplate.keys(statsPattern).flatMap(redisTemplate::delete),
                        redisTemplate.keys(userPattern).flatMap(redisTemplate::delete)
                ).then()
                .doOnSuccess(v -> log.info("Statistics cache cleared"))
                .doOnError(error -> log.error("Failed to clear statistics cache", error));
    }

    @Override
    public Flux<String> getAllStatisticsKeys() {
        return redisTemplate.keys(SERVICE_STATS_PREFIX + "*")
                .doOnError(error -> log.warn("Failed to get statistics keys", error));
    }

    @Override
    public Mono<Boolean> hasStatsCache(String serviceId, LocalDate date) {
        String statsKey = SERVICE_STATS_PREFIX + serviceId + ":" + date.toString();
        return redisTemplate.hasKey(statsKey);
    }

    @Override
    public Mono<Void> deleteServiceStats(String serviceId, LocalDate date) {
        String today = date.toString();
        String statsKey = SERVICE_STATS_PREFIX + serviceId + ":" + today;
        String userSetKey = USER_SET_KEY_PREFIX + serviceId + ":" + today;

        return Mono.when(
                        redisTemplate.delete(statsKey),
                        redisTemplate.delete(userSetKey)
                ).then()
                .doOnSuccess(v -> log.debug("Deleted service stats for: {} on {}", serviceId, date))
                .doOnError(error -> log.warn("Failed to delete service stats: {}", serviceId, error));
    }

    /**
     * 更新响应时间统计
     */
    private Mono<Void> updateResponseTimeStats(String statsKey, long responseTimeMs) {
        return redisTemplate.opsForHash().get(statsKey, "total_response_time")
                .cast(String.class)
                .defaultIfEmpty("0")
                .zipWith(redisTemplate.opsForHash().get(statsKey, "max_response_time")
                        .cast(String.class)
                        .defaultIfEmpty("0"))
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

    private int getIntValue(Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    private long getLongValue(Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }
}