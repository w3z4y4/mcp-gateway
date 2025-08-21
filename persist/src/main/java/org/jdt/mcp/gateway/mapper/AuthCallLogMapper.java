package org.jdt.mcp.gateway.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.jdt.mcp.gateway.core.entity.AuthCallLog;

@Mapper
public interface AuthCallLogMapper {

    /**
     * 插入调用日志
     */
    @Insert("""
        INSERT INTO api_call_logs (user_id, service_id, auth_key_id, request_path, 
                                  request_method, client_ip, user_agent, status_code, 
                                  response_time_ms, error_message, created_at)
        VALUES (#{userId}, #{serviceId}, #{authKeyId}, #{requestPath}, 
                #{requestMethod}, #{clientIp}, #{userAgent}, #{statusCode},
                #{responseTimeMs}, #{errorMessage}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AuthCallLog authCallLog);

    /**
     * 记录调用（简化版本，用于异步记录）
     */
    @Insert("""
        INSERT INTO api_call_logs (user_id, service_id, auth_key_id, request_path, request_method, 
                                  client_ip, status_code, created_at)
        VALUES (#{userId}, #{serviceId}, #{auth_key_id}, #{requestPath}, #{requestMethod}, 
                #{clientIp}, #{statusCode}, NOW())
        """)
    void insertSimpleLog(@Param("userId") String userId,
                         @Param("serviceId") String serviceId,
                         @Param("auth_key_id") Long authKeyId,
                         @Param("requestPath") String requestPath,
                         @Param("requestMethod") String requestMethod,
                         @Param("clientIp") String clientIp,
                         @Param("statusCode") Integer statusCode);
}