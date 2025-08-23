package org.jdt.mcp.gateway.service;

import org.jdt.mcp.gateway.core.dto.ServiceStatsData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;

/**
 * Redis统计缓存服务接口
 */
public interface RedisStatsCacheService {

    /**
     * 记录请求统计数据
     */
    Mono<Void> recordRequestStats(String serviceId, String userId, int statusCode, long responseTimeMs);

    /**
     * 获取实时服务统计数据
     */
    Mono<ServiceStatsData> getRealtimeServiceStats(String serviceId);

    /**
     * 从缓存获取服务统计数据
     */
    Mono<Map<String, String>> getServiceStatsFromCache(String serviceId, LocalDate date);

    /**
     * 获取唯一用户数量
     */
    Mono<Long> getUniqueUsersCount(String serviceId, LocalDate date);

    /**
     * 清空所有统计缓存
     */
    Mono<Void> clearStats();

    /**
     * 获取所有统计缓存的键
     */
    Flux<String> getAllStatisticsKeys();

    /**
     * 检查统计缓存是否存在
     */
    Mono<Boolean> hasStatsCache(String serviceId, LocalDate date);

    /**
     * 删除特定服务的统计缓存
     */
    Mono<Void> deleteServiceStats(String serviceId, LocalDate date);
}