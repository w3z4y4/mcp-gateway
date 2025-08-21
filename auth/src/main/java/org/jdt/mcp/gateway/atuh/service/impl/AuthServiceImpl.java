package org.jdt.mcp.gateway.atuh.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.atuh.AuthEnum;
import org.jdt.mcp.gateway.atuh.config.AuthConfiguration;
import org.jdt.mcp.gateway.atuh.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthConfiguration authConfig;
    private final AntPathMatcher pathMatcher;

    public AuthServiceImpl(AuthConfiguration authConfig) {
        this.authConfig = authConfig;
        this.pathMatcher = new AntPathMatcher();
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

        return validateWithStaticKeys(authKey)
                .switchIfEmpty(Mono.just(false));
    }

    @Override
    public Mono<Boolean> validateWithStaticKeys(String authKey) {
        return Mono.fromSupplier(() -> {
            boolean isValid = authConfig.getValidKeys().contains(authKey);
            log.info("Static key validation for key ending with {}: {}",
                    authKey, isValid);
            return isValid;
        });
    }

    @Override
    public Mono<Boolean> validateWithDatabaseService(String authKey) {
        // todo 1. 检查数据库中是否有对应的key
        // todo 2. 记录key的调用记录表
        return Mono.just(true);
    }

    @Override
    public Mono<Boolean> integrationValidate(String path,String ip,String authKey) {
        if (isWhitelistedPath(path)){
            return Mono.just(true);
        }
        if (isAllowedIp(ip)){
            return Mono.just(true);
        }
        if (authConfig.getAuthType() ==  AuthEnum.staticKey){
            return validateAuthKey(authKey);
        }
        return validateWithDatabaseService(authKey);
    }
}
