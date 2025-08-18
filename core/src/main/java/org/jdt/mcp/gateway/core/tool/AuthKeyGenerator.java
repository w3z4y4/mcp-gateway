package org.jdt.mcp.gateway.core.tool;

import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

public class AuthKeyGenerator {

    // 系统固定的密钥盐（可放配置文件中）
    private static final String SECRET_SALT = "MySuperSecretSalt";

    // 生成随机用户密钥（HMAC-SHA256）
    public static String generateKey(String userId, String serviceId) {
        try {
            String data = userId + ":" + serviceId + ":" + System.currentTimeMillis();

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_SALT.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secretKey);

            byte[] hashBytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // 使用 Base64 URL 编码（去掉换行符，更适合URL/JSON）
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);

        } catch (Exception e) {
            throw new RuntimeException("Error while generating auth key", e);
        }
    }

    /**
     * 构建一个实体（默认永不过期）
     */
    public static AuthKeyEntity buildAuthKeyEntity(String userId, String serviceId) {
        String key = generateKey(userId, serviceId);

        return AuthKeyEntity.builder()
                .keyHash(key)   // 存储 Key（生产可换成 hash）
                .userId(userId)
                .MCPServiceId(serviceId)
                .expiresAt(null)   // 默认永不过期
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .build();
    }

    /**
     * 构建一个带有效期的实体（可选）
     */
    public static AuthKeyEntity buildAuthKeyEntityWithExpiry(String userId, String serviceId, long expireHours) {
        String key = generateKey(userId, serviceId);

        return AuthKeyEntity.builder()
                .keyHash(key)
                .userId(userId)
                .MCPServiceId(serviceId)
                .expiresAt(LocalDateTime.now().plusHours(expireHours))
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .build();
    }

    // 可选：生成一个随机明文Key（例如展示给用户），再存储hash到数据库
    public static String generatePlainKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
