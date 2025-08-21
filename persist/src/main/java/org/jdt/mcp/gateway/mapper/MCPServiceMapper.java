package org.jdt.mcp.gateway.mapper;

import org.apache.ibatis.annotations.*;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;

import java.util.List;

@Mapper
public interface MCPServiceMapper {

    @Insert("""
        INSERT INTO mcp_services (service_id, name, description, endpoint, status, 
                                 max_qps, health_check_url, documentation, created_at, updated_at)
        VALUES (#{serviceId}, #{name}, #{description}, #{endpoint}, #{status}, 
                #{maxQps}, #{healthCheckUrl}, #{documentation}, #{createdAt}, #{updatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MCPServiceEntity service);

    @Update("""
        UPDATE mcp_services 
        SET name = #{name}, description = #{description}, endpoint = #{endpoint},
            status = #{status}, max_qps = #{maxQps}, health_check_url = #{healthCheckUrl},
            documentation = #{documentation}, updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    void update(MCPServiceEntity service);

    @Delete("DELETE FROM mcp_services WHERE id = #{id}")
    void deleteById(Long id);

    @Select("SELECT * FROM mcp_services WHERE service_id = #{serviceId}")
    MCPServiceEntity findByServiceId(String serviceId);

    @Select("SELECT * FROM mcp_services WHERE id = #{id}")
    MCPServiceEntity findById(Long id);

    @Select("SELECT * FROM mcp_services WHERE status = #{status}")
    List<MCPServiceEntity> findByStatus(ServiceStatus status);

    @Select("""
        <script>
        SELECT * FROM mcp_services
        <where>
            <if test="status != null">AND status = #{status}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{offset}, #{pageSize}
        </script>
        """)
    List<MCPServiceEntity> findByConditions(@Param("status") ServiceStatus status,
                                            @Param("name") String name,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);

    @Select("""
        <script>
        SELECT COUNT(*) FROM mcp_services
        <where>
            <if test="status != null">AND status = #{status}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
        </where>
        </script>
        """)
    long countByConditions(@Param("status") ServiceStatus status,
                           @Param("name") String name);

    @Select("SELECT * FROM mcp_services ORDER BY created_at DESC")
    List<MCPServiceEntity> findAll();
}
