package org.jdt.mcp.gateway.mapper;

import org.apache.ibatis.annotations.*;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;

import java.util.List;

@Mapper
public interface AuthKeyMapper {

    // todo 1. 逻辑删除 2. 不要使用*
    @Insert("""
        INSERT INTO auth_keys (key_hash, user_id, mcp_service_id, expires_at, 
                              is_active, created_at, last_used_at)
        VALUES (#{keyHash}, #{userId}, #{MCPServiceId}, #{expiresAt}, 
                #{isActive}, #{createdAt}, #{lastUsedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AuthKeyEntity authKey);

    @Update("""
        UPDATE auth_keys 
        SET key_hash = #{keyHash}, user_id = #{userId}, mcp_service_id = #{MCPServiceId},
            expires_at = #{expiresAt}, is_active = #{isActive}, 
            created_at = #{createdAt}, last_used_at = #{lastUsedAt}
        WHERE id = #{id}
        """)
    void update(AuthKeyEntity authKey);

    @Delete("DELETE FROM auth_keys WHERE id = #{id}")
    void deleteById(Long id);

    @Select("SELECT * FROM auth_keys WHERE id = #{id}")
    AuthKeyEntity findById(Long id);

    @Select("SELECT * FROM auth_keys WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AuthKeyEntity> findByUserId(String userId);

    @Select("SELECT * FROM auth_keys WHERE mcp_service_id = #{serviceId} ORDER BY created_at DESC")
    List<AuthKeyEntity> findByServiceId(String serviceId);

    @Select("""
        SELECT * FROM auth_keys 
        WHERE user_id = #{userId} AND mcp_service_id = #{serviceId} 
        ORDER BY created_at DESC
        """)
    List<AuthKeyEntity> findByUserIdAndServiceId(@Param("userId") String userId,
                                                 @Param("serviceId") String serviceId);

    @Select("""
        <script>
        SELECT * FROM auth_keys
        <where>
            <if test="userId != null and userId != ''">AND user_id = #{userId}</if>
            <if test="serviceId != null and serviceId != ''">AND mcp_service_id = #{serviceId}</if>
            <if test="isActive != null">AND is_active = #{isActive}</if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{offset}, #{pageSize}
        </script>
        """)
    List<AuthKeyEntity> findByConditions(@Param("userId") String userId,
                                         @Param("serviceId") String serviceId,
                                         @Param("isActive") Boolean isActive,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    @Select("""
        <script>
        SELECT COUNT(*) FROM auth_keys
        <where>
            <if test="userId != null and userId != ''">AND user_id = #{userId}</if>
            <if test="serviceId != null and serviceId != ''">AND mcp_service_id = #{serviceId}</if>
            <if test="isActive != null">AND is_active = #{isActive}</if>
        </where>
        </script>
        """)
    long countByConditions(@Param("userId") String userId,
                           @Param("serviceId") String serviceId,
                           @Param("isActive") Boolean isActive);

    @Select("""
        SELECT * FROM auth_keys 
        WHERE is_active = true 
        AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY created_at DESC
        """)
    List<AuthKeyEntity> findActiveKeys();


    @Update("""
        UPDATE auth_keys SET is_active = false 
        WHERE user_id = #{userId} AND mcp_service_id = #{serviceId} AND is_active = true
        """)
    int deactivateUserServiceKeys(@Param("userId") String userId, @Param("serviceId") String serviceId);

    /**
     * 根据key哈希值查询认证信息
     */
    @Select("""
        SELECT ak.*, ms.name as service_name 
        FROM auth_keys ak 
        LEFT JOIN mcp_services ms ON ak.mcp_service_id = ms.service_id
        WHERE ak.key_hash = #{keyHash}
        """)
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "keyHash", column = "key_hash"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "MCPServiceId", column = "mcp_service_id"),
            @Result(property = "expiresAt", column = "expires_at"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "lastUsedAt", column = "last_used_at")
    })
    AuthKeyEntity findByKeyHash(String keyHash);

    /**
     * 更新key的最后使用时间
     */
    @Update("UPDATE auth_keys SET last_used_at = NOW() WHERE key_hash = #{keyHash}")
    void updateLastUsedTime(String keyHash);

    /**
     * 检查key是否有效（激活且未过期）
     */
    @Select("""
        SELECT COUNT(*) > 0 
        FROM auth_keys 
        WHERE key_hash = #{keyHash} 
        AND is_active = true 
        AND (expires_at IS NULL OR expires_at > NOW())
        """)
    boolean isValidKey(String keyHash);
}
