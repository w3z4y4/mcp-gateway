package org.jdt.mcp.gateway.atuh.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.atuh.config.AuthConfiguration;
import org.jdt.mcp.gateway.atuh.service.AuthService;
import org.jdt.mcp.gateway.core.entity.AuthCallRecord;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.jdt.mcp.gateway.core.entity.AuthType;
import org.jdt.mcp.gateway.mapper.AuthCallLogMapper;
import org.jdt.mcp.gateway.mapper.AuthKeyMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthConfiguration authConfig;
    private final AntPathMatcher pathMatcher;
    private final AuthKeyMapper authKeyMapper;
    private final AuthCallLogMapper authCallLogMapper;

    private final Sinks.Many<AuthCallRecord> logSink;

    public AuthServiceImpl(AuthConfiguration authConfig,
                           AuthKeyMapper authKeyMapper,
                           AuthCallLogMapper authCallLogMapper) {
        this.authConfig = authConfig;
        this.pathMatcher = new AntPathMatcher();
        this.authKeyMapper = authKeyMapper;
        this.authCallLogMapper = authCallLogMapper;
        // 初始化异步日志处理流
        this.logSink = Sinks.many().multicast().onBackpressureBuffer();
        setupAsyncLogProcessor();
    }

    @Override
    public boolean isWhitelistedPath(String path) {
        return authConfig.getWhitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    public boolean isAllowedIp(String clientIp) {
        if (!authConfig.isEnableIpWhitelist()) {
            return true;
        }
        return authConfig.getAllowedIps().contains(clientIp);
    }

    @Override
    public Mono<Boolean> validateAuthKey(String authKey) {
        if (!authConfig.isEnabled()) {
            log.debug("Authentication is disabled");
            return Mono.just(true);
        }

        if (authKey == null || authKey.trim().isEmpty()) {
            log.debug("Auth key is null or empty");
            return Mono.just(false);
        }

        // 根据配置选择验证方式
        if (authConfig.getAuthType() == AuthType.staticKey) {
            return validateWithStaticKeys(authKey);
        } else {
            return validateWithDatabaseService(authKey);
        }
    }

    @Override
    public Mono<Boolean> validateWithStaticKeys(String authKey) {
        return Mono.fromSupplier(() -> {
            boolean isValid = authConfig.getValidKeys().contains(authKey);
            log.info("Static key validation for key ending with {}: {}",
                    maskKey(authKey), isValid);
            return isValid;
        });
    }

    @Override
    public Mono<Boolean> validateWithDatabaseService(String authKey) {
        return Mono.fromCallable(() -> {
                    // 1. 检查数据库中是否有对应的key
                    boolean isValid = authKeyMapper.isValidKey(authKey);

                    if (isValid) {
                        log.info("Database key validation successful for key: {}", maskKey(authKey));

                        // 2. 异步更新最后使用时间
                        CompletableFuture.runAsync(() -> {
                            try {
                                authKeyMapper.updateLastUsedTime(authKey);
                            } catch (Exception e) {
                                log.warn("Failed to update last used time for key: {}",
                                        maskKey(authKey), e);
                            }
                        }, Schedulers.boundedElastic().createWorker()::schedule);

                        return true;
                    } else {
                        log.warn("Database key validation failed for key: {}", maskKey(authKey));
                        return false;
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(throwable -> {
                    log.error("Database validation error for key: {}", maskKey(authKey), throwable);
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Boolean> integrationValidate(String path, String ip, String authKey) {
        // 1. 检查路径白名单
        if (isWhitelistedPath(path)) {
            log.debug("Path {} is whitelisted, allowing access", path);
            return Mono.just(true);
        }

        // 2. 检查IP白名单（如果启用）
        if (authConfig.isEnableIpWhitelist() && !isAllowedIp(ip)) {
            log.warn("IP {} is not in whitelist", ip);
            return Mono.just(false);
        }

        // 3. 根据配置进行key验证
        return validateAuthKey(authKey)
                .doOnNext(isValid -> {
                    // 使用logSink异步发送日志记录事件
                    if (authKey != null) { // 无论验证成功失败都记录
                        AuthCallRecord record = new AuthCallRecord(path, ip, authKey, isValid);
                        Sinks.EmitResult emitResult = logSink.tryEmitNext(record);

                        // 如果发送失败，记录错误但不影响主流程
                        if (emitResult.isFailure()) {
                            log.warn("Failed to emit auth call record: {}, fallback to sync logging", emitResult);
                            // 降级到同步记录（可选）
                            CompletableFuture.runAsync(() -> {
                                try {
                                    recordAuthCallSync(path, ip, authKey, isValid);
                                } catch (Exception e) {
                                    log.error("Fallback sync logging also failed", e);
                                }
                            });
                        } else {
                            log.debug("Auth call record emitted successfully for key: {}", maskKey(authKey));
                        }
                    }
                });
    }

    /**
     * 设置异步日志处理器
     */
    private void setupAsyncLogProcessor() {
        logSink.asFlux()
                .onBackpressureDrop(dropped -> log.warn("Dropped log record due to backpressure: {}", dropped))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        record -> {
                            try {
                                recordAuthCallSync(record.path(), record.ip(), record.authKey(), record.success());
                            } catch (Exception e) {
                                log.error("Error processing async log record: {}", record, e);
                            }
                        },
                        error -> {
                            log.error("Error in async log processor, restarting...", error);
                            // 重启日志处理器
                            setupAsyncLogProcessor();
                        },
                        () -> {
                            log.warn("Async log processor completed, restarting...");
                            // 重启日志处理器
                            setupAsyncLogProcessor();
                        }
                );

        log.info("Async log processor started successfully");
    }

    /**
     * 记录认证调用日志（同步方式）
     */
    private void recordAuthCallSync(String path, String ip, String authKey, boolean success) {
        try {
            // 获取认证key信息
            AuthKeyEntity authKeyEntity = authKeyMapper.findByKeyHash(authKey);
            if (authKeyEntity != null) {
                // 记录调用日志
                authCallLogMapper.insertSimpleLog(
                        authKeyEntity.getUserId(),
                        authKeyEntity.getMCPServiceId(),
                        authKeyEntity.getId(),
                        path,
                        "GET", // 这里可以从请求中获取实际方法
                        ip,
                        success ? 200 : 403
                );
                log.debug("Recorded auth call for user: {}, service: {}, success: {}",
                        authKeyEntity.getUserId(), authKeyEntity.getMCPServiceId(), success);
            } else {
                log.warn("Auth key entity not found for key: {}", maskKey(authKey));
            }
        } catch (Exception e) {
            log.error("Error recording auth call for key: {}", maskKey(authKey), e);
        }
    }

    /**
     * 脱敏显示key
     */
    private String maskKey(String authKey) {
        if (authKey == null || authKey.length() < 4) {
            return "***";
        }
        return "***" + authKey.substring(authKey.length() - 4);
    }
}