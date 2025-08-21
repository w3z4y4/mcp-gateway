-- 创建数据库
CREATE DATABASE IF NOT EXISTS mcp_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mcp_gateway;

-- MCP服务表
CREATE TABLE IF NOT EXISTS mcp_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    service_id VARCHAR(100) NOT NULL UNIQUE COMMENT '服务唯一标识',
    name VARCHAR(200) NOT NULL COMMENT '服务名称',
    description TEXT COMMENT '服务描述',
    endpoint VARCHAR(500) NOT NULL COMMENT '服务端点URL',
    status ENUM('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DEPRECATED') NOT NULL DEFAULT 'ACTIVE' COMMENT '服务状态',
    max_qps INT NOT NULL DEFAULT 1000 COMMENT '最大QPS限制',
    health_check_url VARCHAR(500) COMMENT '健康检查URL',
    documentation TEXT COMMENT '服务文档',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_service_id (service_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务表';

-- 认证密钥表
CREATE TABLE IF NOT EXISTS auth_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    key_hash VARCHAR(500) NOT NULL UNIQUE COMMENT '密钥哈希值',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    mcp_service_id VARCHAR(100) NOT NULL COMMENT '关联的MCP服务ID',
    expires_at DATETIME NULL COMMENT '过期时间，NULL表示永不过期',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_used_at DATETIME NULL COMMENT '最后使用时间',

    INDEX idx_key_hash (key_hash),
    INDEX idx_user_id (user_id),
    INDEX idx_service_id (mcp_service_id),
    INDEX idx_user_service (user_id, mcp_service_id),
    INDEX idx_active_keys (is_active, expires_at),
    INDEX idx_created_at (created_at),

    FOREIGN KEY (mcp_service_id) REFERENCES mcp_services(service_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证密钥表';

-- 插入测试数据
INSERT INTO mcp_services (service_id, name, description, endpoint, status, max_qps, health_check_url, documentation) VALUES
('hr-service', '人力服务', '提供查询工作单位的服务', 'http://localhost:8089', 'ACTIVE', 10, 'http://localhost:8089/', '支持按照人名手机号查询工作单位的服务');

-- 创建调用日志表（可选，用于记录API调用）
CREATE TABLE IF NOT EXISTS api_call_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    service_id VARCHAR(100) NOT NULL COMMENT '服务ID',
    auth_key_id BIGINT COMMENT '使用的认证密钥ID',
    request_path VARCHAR(500) COMMENT '请求路径',
    request_method VARCHAR(10) COMMENT '请求方法',
    client_ip VARCHAR(45) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    status_code INT COMMENT '响应状态码',
    response_time_ms INT COMMENT '响应时间(毫秒)',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',

    INDEX idx_user_id (user_id),
    INDEX idx_service_id (service_id),
    INDEX idx_auth_key_id (auth_key_id),
    INDEX idx_created_at (created_at),
    INDEX idx_user_service_time (user_id, service_id, created_at),

    FOREIGN KEY (auth_key_id) REFERENCES auth_keys(id) ON DELETE SET NULL,
    FOREIGN KEY (service_id) REFERENCES mcp_services(service_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API调用日志表';

-- 创建服务统计表（可选，用于统计分析）
CREATE TABLE IF NOT EXISTS service_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    service_id VARCHAR(100) NOT NULL COMMENT '服务ID',
    date_key DATE NOT NULL COMMENT '统计日期',
    total_calls INT NOT NULL DEFAULT 0 COMMENT '总调用次数',
    success_calls INT NOT NULL DEFAULT 0 COMMENT '成功调用次数',
    failed_calls INT NOT NULL DEFAULT 0 COMMENT '失败调用次数',
    avg_response_time_ms INT COMMENT '平均响应时间(毫秒)',
    max_response_time_ms INT COMMENT '最大响应时间(毫秒)',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_service_date (service_id, date_key),
    INDEX idx_date_key (date_key),
    INDEX idx_service_id (service_id),

    FOREIGN KEY (service_id) REFERENCES mcp_services(service_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务统计表';