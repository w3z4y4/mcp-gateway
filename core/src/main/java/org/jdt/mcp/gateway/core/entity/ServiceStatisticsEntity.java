package org.jdt.mcp.gateway.core.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ServiceStatisticsEntity {

    private Long id;

    /**
     * 服务ID
     */
    private String serviceId;

    /**
     * 统计日期
     */
    private LocalDate dateKey;

    /**
     * 总调用次数
     */
    private Integer totalCalls = 0;

    /**
     * 成功调用次数
     */
    private Integer successCalls = 0;

    /**
     * 失败调用次数
     */
    private Integer failedCalls = 0;

    /**
     * 平均响应时间(毫秒)
     */
    private Integer avgResponseTimeMs;

    /**
     * 最大响应时间(毫秒)
     */
    private Integer maxResponseTimeMs;

    /**
     * 独立用户数
     */
    private Integer uniqueUsers = 0;

    /**
     * 逻辑删除标识
     */
    private Integer isDeleted = 0;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}