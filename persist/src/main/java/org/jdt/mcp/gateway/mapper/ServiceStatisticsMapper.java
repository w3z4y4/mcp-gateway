package org.jdt.mcp.gateway.mapper;

import org.apache.ibatis.annotations.*;
import org.jdt.mcp.gateway.core.entity.ServiceStatisticsEntity;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ServiceStatisticsMapper {

    /**
     * 插入或更新服务统计数据
     */
    @Insert("""
        INSERT INTO service_statistics 
        (service_id, date_key, total_calls, success_calls, failed_calls, 
         avg_response_time_ms, max_response_time_ms, unique_users, created_at, updated_at)
        VALUES (#{serviceId}, #{dateKey}, #{totalCalls}, #{successCalls}, #{failedCalls}, 
                #{avgResponseTimeMs}, #{maxResponseTimeMs}, #{uniqueUsers}, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
        total_calls = total_calls + VALUES(total_calls),
        success_calls = success_calls + VALUES(success_calls),
        failed_calls = failed_calls + VALUES(failed_calls),
        avg_response_time_ms = CASE 
            WHEN (total_calls + VALUES(total_calls)) > 0 
            THEN (avg_response_time_ms * total_calls + VALUES(avg_response_time_ms) * VALUES(total_calls)) / (total_calls + VALUES(total_calls))
            ELSE 0 
        END,
        max_response_time_ms = GREATEST(max_response_time_ms, VALUES(max_response_time_ms)),
        unique_users = VALUES(unique_users),
        updated_at = NOW()
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertOrUpdate(ServiceStatisticsEntity statistics);

    /**
     * 根据服务ID和日期查询统计信息
     */
    @Select("""
        SELECT * FROM service_statistics 
        WHERE service_id = #{serviceId} AND date_key = #{dateKey} AND is_deleted = 0
        """)
    ServiceStatisticsEntity findByServiceIdAndDate(@Param("serviceId") String serviceId,
                                                   @Param("dateKey") LocalDate dateKey);

    /**
     * 根据服务ID查询最近的统计信息
     */
    @Select("""
        SELECT * FROM service_statistics 
        WHERE service_id = #{serviceId} AND is_deleted = 0
        ORDER BY date_key DESC 
        LIMIT #{limit}
        """)
    List<ServiceStatisticsEntity> findRecentByServiceId(@Param("serviceId") String serviceId,
                                                        @Param("limit") int limit);

    /**
     * 汇总查询服务统计信息
     */
    @Select("""
        SELECT 
            service_id,
            #{dateKey} as date_key,
            SUM(total_calls) as total_calls,
            SUM(success_calls) as success_calls, 
            SUM(failed_calls) as failed_calls,
            AVG(avg_response_time_ms) as avg_response_time_ms,
            MAX(max_response_time_ms) as max_response_time_ms,
            MAX(unique_users) as unique_users,
            NOW() as created_at,
            NOW() as updated_at
        FROM service_statistics 
        WHERE service_id = #{serviceId} AND date_key = #{dateKey} AND is_deleted = 0
        GROUP BY service_id
        """)
    ServiceStatisticsEntity aggregateByServiceIdAndDate(@Param("serviceId") String serviceId,
                                                        @Param("dateKey") LocalDate dateKey);

    /**
     * 删除过期统计数据（逻辑删除）
     */
    @Update("UPDATE service_statistics SET is_deleted = 1 WHERE date_key < #{beforeDate}")
    int deleteExpiredData(@Param("beforeDate") LocalDate beforeDate);

    /**
     * 获取服务列表
     */
    @Select("SELECT DISTINCT service_id FROM service_statistics WHERE is_deleted = 0")
    List<String> findDistinctServiceIds();
}