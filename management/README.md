# 管理模块 (Management Module)

## 概述

Management模块提供MCP服务的管理功能，包括服务注册、用户认证密钥管理和客户端配置生成。该模块负责整个MCP网关的服务治理和用户权限管理。

## 功能特性

1. **MCP服务管理**：提供服务的CRUD操作、状态管理和健康检查
2. **用户认证密钥管理**：支持密钥申请、撤销、续期等操作
3. **客户端配置生成**：支持Spring AI MCP Client的YAML和JSON配置生成

## API

<span style="color: purple; font-weight: bold;">核心API有🚀标志！</span>

### 1. MCP服务管理API

#### 1.1 🚀创建MCP服务

**接口地址**: `POST /api/management/services`

**请求示例**:
```bash
curl -X POST http://localhost:9080/api/management/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "hr-service",
    "name": "人力服务",
    "description": "提供查询工作单位的服务",
    "endpoint": "http://localhost:8089",
    "status": "ACTIVE",
    "maxQps": 10,
    "healthCheckUrl": "http://localhost:8090/health",
    "documentation": "支持按照人名手机号查询工作单位的服务"
  }'
```

**响应示例**:
```json
{
  "id":1,
  "serviceId":"hr-service",
  "name":"人力服务",
  "description":"提供查询工作单位的服务",
  "endpoint":"http://localhost:8089",
  "status":"ACTIVE",
  "maxQps":10,
  "healthCheckUrl":"http://localhost:8090/health",
  "documentation":"支持按照人名手机号查询工作单位的服务",
  "createdAt":"2025-08-22T11:10:26.430551",
  "updatedAt":"2025-08-22T11:10:26.430588"
}
```

#### 1.2 更新MCP服务

**接口地址**: `PUT /api/management/services/{serviceId}`

**请求示例**:
```bash
curl -X PUT http://localhost:9080/api/management/services/hr-service \
  -H "Content-Type: application/json" \
  -d '{
    "name": "增强版天气查询服务",
    "description": "提供全球天气查询和预报功能",
    "maxQps": 2000,
    "status": "ACTIVE"
  }'
```

#### 1.3 🚀查询服务列表

**接口地址**: `GET /api/management/services`

**请求示例**:
```bash
# 查询所有激活状态的服务，支持分页
curl "http://localhost:9080/api/management/services?status=ACTIVE&page=0&size=10"

# 按名称模糊查询
curl "http://localhost:9080/api/management/services?name=天气&page=0&size=20"
```

**响应示例**:
```json
{
  "content": [
    {
      "id": 1,
      "serviceId": "hr-service",
      "name": "增强版天气查询服务",
      "status": "ACTIVE",
      "maxQps": 2000,
      "createdAt": "2024-01-15T10:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1
}
```

#### 1.4 服务健康检查

**接口地址**: `POST /api/management/services/{serviceId}/health-check`

**请求示例**:
```bash
curl -X POST http://localhost:9080/api/management/services/hr-service/health-check
```

**响应示例**:
```json
true
```

#### 1.5 更新服务状态

**接口地址**: `PATCH /api/management/services/{serviceId}/status`

**请求示例**:
```bash
curl -X PATCH "http://localhost:9080/api/management/services/hr-service/status?status=MAINTENANCE"
```

### 2. 认证密钥管理API

#### 2.1 🚀申请认证密钥

**接口地址**: `POST /api/management/auth-keys/apply`

**请求示例**:
```bash
curl -X POST http://localhost:9080/api/management/auth-keys/apply \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "001025821",
    "serviceId": "hr-service",
    "remarks": "查询"
  }'
```

**响应示例**:
```json
{
  "id": 1,
  "keyHash": "0UqC1yxKDAVb1PAQ75ArLVN0PbvTTKvHdHUz5XCj13Q",
  "userId": "001025821",
  "serviceId": "hr-service",
  "serviceName": "人力服务",
  "expiresAt": null,
  "isActive": true,
  "createdAt": "2025-08-21T10:26:05.722386",
  "lastUsedAt": null,
  "remarks": null
}
```

#### 2.2 🚀查询用户密钥列表

**接口地址**: `GET /api/management/auth-keys/user/{userId}`

**请求示例**:
```bash
curl http://localhost:9080/api/management/auth-keys/user/001025821
```

**响应示例**:
```json
[
  {
    "id": 1,
    "keyHash": "jiT4h3gBuT3CJ2Dz75yFkiX_i4ToUpRI7b-tGTZwIsc",
    "userId": "001025821",
    "serviceId": "hr-service",
    "serviceName": "人力服务",
    "expiresAt": null,
    "isActive": true,
    "createdAt": "2025-08-22T11:12:50",
    "lastUsedAt": null,
    "remarks": null
  }
]
```

#### 2.3 分页查询所有密钥（管理员功能）

**接口地址**: `GET /api/management/auth-keys`

**请求示例**:
```bash
# 查询特定用户的密钥
curl "http://localhost:9080/api/management/auth-keys?userId=001025821&page=0&size=10"

# 查询特定服务的所有密钥
curl "http://localhost:9080/api/management/auth-keys?serviceId=hr-service&page=0&size=20"

# 只查询激活的密钥
curl "http://localhost:9080/api/management/auth-keys?isActive=true&page=0&size=10"
```

#### 2.4 撤销密钥

**接口地址**: `DELETE /api/management/auth-keys/{keyId}`

**请求示例**:
```bash
curl -X DELETE http://localhost:9080/api/management/auth-keys/1
```

#### 2.5 更新密钥状态

**接口地址**: `PATCH /api/management/auth-keys/{keyId}/status`

**请求示例**:
```bash
# 停用密钥
curl -X PATCH "http://localhost:9080/api/management/auth-keys/1/status?isActive=false"

# 激活密钥
curl -X PATCH "http://localhost:9080/api/management/auth-keys/1/status?isActive=true"
```

#### 2.6 续期密钥

**接口地址**: `POST /api/management/auth-keys/{keyId}/renew`

**请求示例**:
```bash
# 延长720小时（30天）
curl -X POST "http://localhost:9080/api/management/auth-keys/1/renew?extendHours=720"

# 设置为永不过期
curl -X POST "http://localhost:9080/api/management/auth-keys/1/renew?extendHours=0"
```

#### 2.7 批量撤销用户服务密钥

**接口地址**: `DELETE /api/management/auth-keys/user/{userId}/service/{serviceId}`

**请求示例**:
```bash
curl -X DELETE http://localhost:9080/api/management/auth-keys/user/001025821/service/hr-service
```

**响应示例**:
```json
2
```

### 3. 配置生成API

#### 3.1 生成YAML配置

**接口地址**: `POST /api/management/config/yaml`

**请求示例**:
```bash
curl -X POST http://localhost:9080/api/management/config/yaml \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "001025821",
    "serviceIds": ["hr-service", "hr-service"],
    "baseUrl": "http://localhost:9080",
    "toolCallbackEnable": true,
    "timeout": 60,
    "autoApprove": false
  }'
```

**响应示例**:
```yaml
spring:
  ai:
    mcp:
      client:
        toolcallback:
          enable: true
        sse:
          connections:
            hr-service:
              url: http://localhost:9080/mcp/hr-service?key=ak_2b3c4d5e6f7g8h9i0j1a
        type: async
```

#### 3.2 生成JSON配置

**接口地址**: `POST /api/management/config/json`

**请求示例**:
```bash
curl -X POST http://localhost:9080/api/management/config/json \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "001025821",
    "serviceIds": ["hr-service"],
    "baseUrl": "http://localhost:9080",
    "timeout": 30
  }'
```

**响应示例**:
```json
{
  "hr-service": {
    "url": "http://localhost:9080/mcp/hr-service?key=ak_1a2b3c4d5e6f7g8h9i0j",
    "type": "sse",
    "timeout": 30,
    "disabled": false
  }
}
```

#### 3.3 🚀 预览配置

**接口地址**: `POST /api/management/config/yaml/preview` 或 `POST /api/management/config/json/preview`

**请求示例**:
```bash
# 预览YAML配置（不触发下载）
curl -X POST http://localhost:9080/api/management/config/yaml/preview \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "001025821",
    "serviceIds": ["hr-service"]
  }'
```

**响应示例**:
```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            hr-service:
              url: http://localhost:9080/mcp/hr-service?key=jiT4h3gBuT3CJ2Dz75yFkiX_i4ToUpRI7b-tGTZwIsc
        toolcallback:
          enable: true
        type: async
```

#### 3.4 获取用户可用服务列表

**接口地址**: `GET /api/management/config/available-services/{userId}`

**请求示例**:
```bash
curl http://localhost:9080/api/management/config/available-services/001025821
```

**响应示例**:
```json
[
  {
    "serviceId": "hr-service",
    "serviceName": "增强版天气查询服务",
    "endpoint": "http://localhost:8090",
    "authKey": "ak_1a2b3c4d5e6f7g8h9i0j",
    "maxQps": 2000,
    "isActive": true,
    "description": "提供全球天气查询和预报功能"
  }
]
```

#### 3.5 验证配置生成请求

**接口地址**: `POST /api/management/config/validate`

**请求示例**:
```bash
curl -X POST http://localhost:9080/api/management/config/validate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "001025821",
    "serviceIds": ["hr-service", "invalid-service"],
    "timeout": -1
  }'
```

**响应示例**:
```json
{
  "isValid": false,
  "errors": [
    "Service not found or not authorized: invalid-service",
    "Timeout must be positive"
  ],
  "warnings": [],
  "validServiceCount": 1,
  "totalServiceCount": 2
}
```

## 数据库设计

### 核心表结构

- **mcp_services**: MCP服务信息表
- **auth_keys**: 认证密钥表
- **api_call_logs**: API调用日志表（可选）
- **service_statistics**: 服务统计表（可选）

详细建表语句参见 `ddl.sql` 文件。

## 配置说明

### application.yml 配置项

```yaml
server:
  port: 9080  # 管理服务端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mcp_gateway
    username: root
    password: McpDB123

# MyBatis配置
mybatis:
  type-aliases-package: org.jdt.mcp.gateway.core.entity
  configuration:
    map-underscore-to-camel-case: true
```

## 错误处理

所有API均提供统一的错误响应格式：

```json
{
  "timestamp": "2024-01-15T10:30:00",
  "error": "Bad Request",
  "message": "Service ID already exists: hr-service"
}
```

## 部署建议

1. **数据库连接池**: 根据并发量调整连接池大小
2. **监控告警**: 配置服务健康检查和告警机制
3. **日志管理**: 配置日志轮转和集中收集
4. **备份策略**: 定期备份服务配置和密钥数据

## TODO

- [ ] Management模块切换到WebFlux（当前使用Spring MVC）
- [ ] 增加服务版本管理功能
- [ ] 支持服务依赖关系配置
- [ ] 增加更细粒度的权限控制
- [ ] 支持服务配置的热更新