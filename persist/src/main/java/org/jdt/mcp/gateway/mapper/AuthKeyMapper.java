package org.jdt.mcp.gateway.mapper;

import org.apache.ibatis.annotations.*;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;

import java.util.List;

@Mapper
public interface AuthKeyMapper {

    @Insert("""
        INSERT INTO auth_keys (key_hash, user_id, mcp_service_id, expires_at, 
                              is_active, created_at, last_used_at)
        VALUES (#{keyHash}, #{userId}, #{MCPServiceId}, #{expiresAt}, 
                #{isActive}, #{createdAt}, #{lastUsedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AuthKeyEntity authKey);

    /**
     * 逻辑删除 - 软删除
     */
    @Update("UPDATE auth_keys SET is_deleted = 1, updated_at = NOW() WHERE id = #{id}")
    void deleteById(Long id);

    /**
     * 批量逻辑删除
     */
    @Update("UPDATE auth_keys SET is_deleted = 1, updated_at = NOW() WHERE user_id = #{userId}")
    void deleteByUserId(String userId);

    @Update("""
        UPDATE auth_keys 
        SET key_hash = #{keyHash}, user_id = #{userId}, mcp_service_id = #{MCPServiceId},
            expires_at = #{expiresAt}, is_active = #{isActive}, 
            last_used_at = #{lastUsedAt}, updated_at = NOW()
        WHERE id = #{id} AND is_deleted = 0
        """)
    void update(AuthKeyEntity authKey);

    @Select("SELECT * FROM auth_keys WHERE id = #{id} AND is_deleted = 0")
    AuthKeyEntity findById(Long id);

    @Select("SELECT * FROM auth_keys WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<AuthKeyEntity> findByUserId(String userId);

    @Select("SELECT * FROM auth_keys WHERE mcp_service_id = #{serviceId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<AuthKeyEntity> findByServiceId(String serviceId);

    @Select("""
        SELECT * FROM auth_keys 
        WHERE user_id = #{userId} AND mcp_service_id = #{serviceId} AND is_deleted = 0
        ORDER BY created_at DESC
        """)
    List<AuthKeyEntity> findByUserIdAndServiceId(@Param("userId") String userId,
                                                 @Param("serviceId") String serviceId);

    @Select("""
        <script>
        SELECT ak.*, ms.name as service_name
        FROM auth_keys ak
        LEFT JOIN mcp_services ms ON ak.mcp_service_id = ms.service_id AND ms.is_deleted = 0
        <where>
            ak.is_deleted = 0
            <if test="userId != null and userId != ''">AND ak.user_id = #{userId}</if>
            <if test="serviceId != null and serviceId != ''">AND ak.mcp_service_id = #{serviceId}</if>
            <if test="isActive != null">AND ak.is_active = #{isActive}</if>
        </where>
        ORDER BY ak.created_at DESC
        LIMIT #{offset}, #{pageSize}
        </script>
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
    List<AuthKeyEntity> findByConditions(@Param("userId") String userId,
                                         @Param("serviceId") String serviceId,
                                         @Param("isActive") Boolean isActive,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    @Select("""
        <script>
        SELECT COUNT(*) FROM auth_keys ak
        LEFT JOIN mcp_services ms ON ak.mcp_service_id = ms.service_id AND ms.is_deleted = 0
        <where>
            ak.is_deleted = 0
            <if test="userId != null and userId != ''">AND ak.user_id = #{userId}</if>
            <if test="serviceId != null and serviceId != ''">AND ak.mcp_service_id = #{serviceId}</if>
            <if test="isActive != null">AND ak.is_active = #{isActive}</if>
        </where>
        </script>
        """)
    long countByConditions(@Param("userId") String userId,
                           @Param("serviceId") String serviceId,
                           @Param("isActive") Boolean isActive);

    @Select("""
        SELECT ak.* FROM auth_keys ak
        LEFT JOIN mcp_services ms ON ak.mcp_service_id = ms.service_id
        WHERE ak.is_active = true 
        AND ak.is_deleted = 0
        AND ms.is_deleted = 0
        AND (ak.expires_at IS NULL OR ak.expires_at > NOW())
        ORDER BY ak.created_at DESC
        """)
    List<AuthKeyEntity> findActiveKeys();

    @Update("""
        UPDATE auth_keys SET is_active = false, updated_at = NOW()
        WHERE user_id = #{userId} AND mcp_service_id = #{serviceId} 
        AND is_active = true AND is_deleted = 0
        """)
    int deactivateUserServiceKeys(@Param("userId") String userId, @Param("serviceId") String serviceId);

    /**
     * 根据key哈希值查询认证信息（增加服务有效性检查）
     */
    @Select("""
        SELECT ak.*, ms.name as service_name 
        FROM auth_keys ak 
        LEFT JOIN mcp_services ms ON ak.mcp_service_id = ms.service_id AND ms.is_deleted = 0
        WHERE ak.key_hash = #{keyHash} AND ak.is_deleted = 0
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
    @Update("UPDATE auth_keys SET last_used_at = NOW() WHERE key_hash = #{keyHash} AND is_deleted = 0")
    void updateLastUsedTime(String keyHash);

    /**
     * 检查key是否有效（激活且未过期，且关联服务未删除）
     */
    @Select("""
        SELECT COUNT(*) > 0 
        FROM auth_keys ak
        LEFT JOIN mcp_services ms ON ak.mcp_service_id = ms.service_id
        WHERE ak.key_hash = #{keyHash} 
        AND ak.is_active = true 
        AND ak.is_deleted = 0
        AND ms.is_deleted = 0
        AND ms.status = 'ACTIVE'
        AND (ak.expires_at IS NULL OR ak.expires_at > NOW())
        """)
    boolean isValidKey(String keyHash);

    /**
     * 检查服务是否存在且有效（业务层数据完整性检查）
     */
    @Select("""
        SELECT COUNT(*) > 0 
        FROM mcp_services 
        WHERE service_id = #{serviceId} AND is_deleted = 0
        """)
    boolean isServiceExists(String serviceId);
}