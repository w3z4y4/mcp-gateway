package org.jdt.mcp.gateway.auth.service;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 会话认证服务接口
 */
public interface SessionAuthService {

    /**
     * 存储sessionId与authKey的关联关系
     * @param sessionId 会话ID
     * @param authKey 认证key
     * @param ttl 过期时间，null表示使用默认过期时间
     * @return Mono<Void>
     */
    Mono<Void> storeSessionAuthKey(String sessionId, String authKey, Duration ttl);

    /**
     * 根据sessionId获取对应的authKey
     * @param sessionId 会话ID
     * @return 对应的authKey，如果不存在或已过期返回null
     */
    Mono<String> getAuthKeyBySessionId(String sessionId);

    /**
     * 验证sessionId并返回对应的authKey
     * @param sessionId 会话ID
     * @return 如果sessionId有效返回对应的authKey，否则返回null
     */
    Mono<String> validateSessionId(String sessionId);

    /**
     * 删除sessionId对应的认证关系
     * @param sessionId 会话ID
     * @return Mono<Void>
     */
    Mono<Void> removeSessionAuthKey(String sessionId);

    /**
     * 延长session的过期时间
     * @param sessionId 会话ID
     * @param ttl 新的过期时间
     * @return 是否成功延长
     */
    Mono<Boolean> extendSessionTtl(String sessionId, Duration ttl);

    /**
     * 检查session是否存在
     * @param sessionId 会话ID
     * @return 是否存在
     */
    Mono<Boolean> sessionExists(String sessionId);

    /**
     * 获取session的剩余过期时间
     * @param sessionId 会话ID
     * @return 剩余时间（秒），-1表示永不过期，-2表示不存在
     */
    Mono<Long> getSessionTtl(String sessionId);

    /**
     * 清理所有过期的session
     * @return 清理的数量
     */
    Mono<Long> cleanExpiredSessions();
}