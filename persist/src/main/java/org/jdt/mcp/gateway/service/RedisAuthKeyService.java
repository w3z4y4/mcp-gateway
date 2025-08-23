package org.jdt.mcp.gateway.service;

import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;

public interface RedisAuthKeyService {

    /**
     * 缓存认证key信息
     */
    void cacheAuthKey(String authKey, AuthKeyEntity entity);
    /**
     * 从缓存获取认证key信息
     */
    AuthKeyEntity getAuthKeyFromCache(String authKey);
    /**
     * 缓存无效key（防止频繁查询数据库）
     */
    void cacheInvalidKey(String authKey);
    /**
     * 检查是否为已缓存的无效key
     */
    boolean isInvalidKeyCached(String authKey);
    /**
     * 删除认证key缓存（密钥被撤销时）
     */
    void evictAuthKey(String authKey);
    /**
     * 批量删除用户服务相关的缓存
     */
    void evictUserServiceKeys(String userId, String serviceId);
    /**
     * 更新key的最后使用时间（异步）
     */
    void updateLastUsedTime(String authKey);
}
