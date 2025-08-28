package org.jdt.mcp.gateway.auth.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.service.RedisAuthKeyService;
import org.jdt.mcp.gateway.auth.config.AuthConfiguration;
import org.jdt.mcp.gateway.auth.service.AuthService;
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

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthConfiguration authConfig;
    private final AntPathMatcher pathMatcher;
    private final AuthKeyMapper authKeyMapper;
    private final AuthCallLogMapper authCallLogMapper;
    private final RedisAuthKeyService redisAuthKeyService;

    private final Sinks.Many<AuthCallRecord> logSink;

    public AuthServiceImpl(AuthConfiguration authConfig,
                           AuthKeyMapper authKeyMapper,
                           AuthCallLogMapper authCallLogMapper,
                           RedisAuthKeyService redisAuthKeyService) {
        this.authConfig = authConfig;
        this.pathMatcher = new AntPathMatcher();
        this.authKeyMapper = authKeyMapper;
        this.authCallLogMapper = authCallLogMapper;
        this.redisAuthKeyService = redisAuthKeyService;
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
        return redisAuthKeyService.isInvalidKeyCached(authKey)
                .flatMap(isInvalid -> {
                    if (isInvalid) {
                        log.debug("Auth key found in invalid cache: {}", maskKey(authKey));
                        return Mono.just(false);
                    }

                    // 从Redis缓存获取认证key信息
                    return redisAuthKeyService.getAuthKeyFromCache(authKey)
                            .flatMap(cachedEntity -> {
                                log.debug("Auth key found in cache: {}", maskKey(authKey));
                                boolean isValid = isAuthKeyValid(cachedEntity);

                                if (isValid) {
                                    // 异步更新最后使用时间
                                    redisAuthKeyService.updateLastUsedTime(authKey)
                                            .doOnError(error -> log.warn("Failed to update last used time for cached key: {}",
                                                    maskKey(authKey), error))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe();

                                    // 异步更新数据库
                                    Mono.fromRunnable(() -> {
                                                try {
                                                    authKeyMapper.updateLastUsedTime(authKey);
                                                } catch (Exception e) {
                                                    log.warn("Failed to update last used time in database for key: {}",
                                                            maskKey(authKey), e);
                                                }
                                            }).subscribeOn(Schedulers.boundedElastic())
                                            .subscribe();
                                }

                                return Mono.just(isValid);
                            })
                            .switchIfEmpty(
                                    // 缓存未命中，查询数据库
                                    Mono.fromCallable(() -> {
                                        log.debug("Cache miss, querying database for key: {}", maskKey(authKey));
                                        AuthKeyEntity dbEntity = authKeyMapper.findByKeyHash(authKey);

                                        if (dbEntity == null) {
                                            log.warn("Auth key not found in database: {}", maskKey(authKey));
                                            // 异步缓存无效key
                                            redisAuthKeyService.cacheInvalidKey(authKey)
                                                    .doOnError(error -> log.warn("Failed to cache invalid key", error))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .subscribe();
                                            return false;
                                        }

                                        // 异步缓存到Redis
                                        redisAuthKeyService.cacheAuthKey(authKey, dbEntity)
                                                .doOnError(error -> log.warn("Failed to cache auth key", error))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .subscribe();

                                        boolean isValid = isAuthKeyValid(dbEntity);
                                        if (isValid) {
                                            log.info("Database key validation successful for key: {}", maskKey(authKey));

                                            // 异步更新最后使用时间
                                            Mono.fromRunnable(() -> {
                                                        try {
                                                            authKeyMapper.updateLastUsedTime(authKey);
                                                        } catch (Exception e) {
                                                            log.warn("Failed to update last used time for key: {}",
                                                                    maskKey(authKey), e);
                                                        }
                                                    }).subscribeOn(Schedulers.boundedElastic())
                                                    .subscribe();

                                            // 异步更新Redis缓存中的时间
                                            redisAuthKeyService.updateLastUsedTime(authKey)
                                                    .doOnError(error -> log.warn("Failed to update last used time in cache", error))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .subscribe();
                                        } else {
                                            log.warn("Database key validation failed for key: {}", maskKey(authKey));
                                        }

                                        return isValid;
                                    }).subscribeOn(Schedulers.boundedElastic())
                            );
                })
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
                            // 降级到异步记录
                            Mono.fromRunnable(() -> {
                                        try {
                                            recordAuthCallSync(path, ip, authKey, isValid);
                                        } catch (Exception e) {
                                            log.error("Fallback sync logging also failed", e);
                                        }
                                    }).subscribeOn(Schedulers.boundedElastic())
                                    .subscribe();
                        } else {
                            log.debug("Auth call record emitted successfully for key: {}", maskKey(authKey));
                        }
                    }
                });
    }

    /**
     * 检查AuthKeyEntity是否有效
     */
    private boolean isAuthKeyValid(AuthKeyEntity entity) {
        if (!entity.getIsActive()) {
            return false;
        }

        // 检查过期时间
        return entity.getExpiresAt() == null || !entity.getExpiresAt().isBefore(java.time.LocalDateTime.now());
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