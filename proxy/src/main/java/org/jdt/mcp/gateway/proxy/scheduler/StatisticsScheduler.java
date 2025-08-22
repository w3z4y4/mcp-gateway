package org.jdt.mcp.gateway.proxy.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.proxy.service.StatisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计数据定时任务调度器
 * 负责定期将Redis中的统计数据刷新到MySQL数据库
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "jdt.mcp.proxy.enable-statistics", havingValue = "true", matchIfMissing = true)
public class StatisticsScheduler {

    private final StatisticsService statisticsService;

    public StatisticsScheduler(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 每小时刷新统计数据到数据库
     * 在每小时的第5分钟执行，避免与其他任务冲突
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void flushStatistics() {
        log.debug("Starting 5 min statistics flush to database");

        statisticsService.flushStatisticsToDatabase()
                .doOnSuccess(v -> log.info("Hourly statistics flush completed successfully"))
                .doOnError(error -> log.error("Hourly statistics flush failed", error))
                .subscribe();
    }

    /**
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void flushStatisticsHourly() {
        log.info("Starting hourly statistics flush to database");

        statisticsService.flushStatisticsToDatabase()
                .doOnSuccess(v -> log.info("Daily statistics flush completed successfully"))
                .doOnError(error -> log.error("Daily statistics flush failed", error))
                .subscribe();
    }

}