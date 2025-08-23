package org.jdt.mcp.gateway.auth.service;

import reactor.core.publisher.Mono;

public interface AuthService {
    boolean isWhitelistedPath(String path);
    boolean isAllowedIp(String clientIp);
    Mono<Boolean> validateAuthKey(String authKey);
    Mono<Boolean> validateWithStaticKeys(String authKey);
    Mono<Boolean> validateWithDatabaseService(String authKey);

    /**
     * 综合鉴权检查
     * @param path 请求
     * @param ip ip
     * @param authKey key
     * @return 检查结果
     * 1. 检查路径白名单
     * 2. 检查ip白名单
     * 3. 根据key检查
     */
    Mono<Boolean> integrationValidate(String path,String ip,String authKey);
}
