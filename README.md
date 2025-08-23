# MCP Gateway - 企业级MCP API网关

🚀 为MCP生态系统提供高性能协议转换、服务管理、鉴权和流量治理的完整解决方案

## 项目概述

MCP Gateway是一个基于Spring Boot 3.x的企业级API网关系统，专门为Model Context Protocol (MCP)服务提供统一的接入、管理和代理功能。系统采用模块化架构设计，确保高可扩展性、可维护性和安全性。

## 核心特性

- 🔐 **统一鉴权**: 支持密钥认证、IP白名单、路径白名单
- 🚀 **高性能代理**: 基于WebFlux响应式架构，支持异步请求处理
- 📊 **服务治理**: 服务发现、健康检查、统计监控
- 🛡️ **流量控制**: 多维度限流、熔断降级
- 💾 **数据持久化**: MySQL存储配置数据，Redis缓存热点数据
- 📈 **实时监控**: 服务调用统计、性能监控

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Applications                    │
└─────────────────────┬───────────────────────────────────────┘
                      │ HTTP/HTTPS Requests
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    MCP Gateway                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │    Auth     │  │    Proxy    │  │     Management      │  │
│  │   Module    │  │   Module    │  │      Module         │  │
│  │             │  │             │  │                     │  │
│  │ - 密钥认证   │  │ - 协议转换   │  │ - 服务管理          │  │
│  │ - IP白名单   │  │ - 请求代理   │  │ - 配置生成          │  │
│  │ - 调用记录   │  │ - 流量统计   │  │ - 密钥管理          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────┬───────────────────────────────────────┘
                      │ MCP Protocol
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  Backend MCP Services                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Weather   │  │    HR       │  │     File            │  │
│  │   Service   │  │   Service   │  │    Service          │  │
│  │ :8081       │  │ :8082       │  │    :8083            │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 技术栈

- **后端框架**: Spring Boot 3.5.4, Spring WebFlux
- **数据库**: MySQL 8.0+ (主存储), Redis 6.0+ (缓存)
- **ORM框架**: MyBatis
- **构建工具**: Maven 3.8+
- **JDK版本**: Java 17+
- **容器化**: Docker (可选)

## 模块结构

```
mcp-gateway/
├── auth/                       # 鉴权模块
│   ├── src/main/java/
│   │   └── org/jdt/mcp/gateway/auth/
│   │       ├── config/         # 认证配置类
│   │       ├── filter/         # 认证过滤器
│   │       ├── service/        # 认证服务实现
│   │       └── tool/           # 认证工具类
│   └── pom.xml
├── proxy/                      # 代理模块
│   ├── src/main/java/
│   │   └── org/jdt/mcp/gateway/proxy/
│   │       ├── config/         # 代理配置类
│   │       ├── ctl/            # REST控制器
│   │       ├── handler/        # 请求处理器
│   │       ├── service/        # 代理服务实现
│   │       └── scheduler/      # 定时任务调度器
│   └── pom.xml
├── management/                 # 管理模块 📋
│   ├── src/main/java/
│   │   └── org/jdt/mcp/gateway/management/
│   │       ├── ManagementApp.java              # 主启动类
│   │       ├── config/                         # 配置类
│   │       ├── ctl/                            # REST控制器
│   │       └── service/                        # 业务服务层
│   ├── src/main/resources/
│   │   └── application.yml                     # 管理模块配置文件
│   └── pom.xml
├── core/                       # 核心共享模块
│   ├── src/main/java/
│   │   └── org/jdt/mcp/gateway/core/
│   │       ├── entity/         # 实体类（数据库映射）
│   │       ├── dto/            # 数据传输对象
│   │       ├── tool/           # 通用工具类
│   │       └── exception/      # 自定义异常类
│   └── pom.xml
├── persist/                    # 数据持久化模块
│   ├── src/main/java/
│   │   └── org/jdt/mcp/gateway/
│   │       └── mapper/         # MyBatis Mapper接口
│   └── pom.xml
├── ddl.sql                     # 数据库建表脚本
├── pom.xml                     # 主POM文件
└── README.md                   # 项目说明文档

# 各模块关系说明

┌─────────────────────────────────────────────────────────┐
│                     Frontend/Client                     │
└─────────────────────┬───────────────────────────────────┘
                      │
    ┌─────────────────┼─────────────────┐
    │                 │                 │
    ▼                 ▼                 ▼
┌─────────┐    ┌─────────────┐    ┌─────────────┐
│  Auth   │    │    Proxy    │    │ Management  │
│ Module  │◄──►│   Module    │◄──►│   Module    │
│ :8080   │    │   :8080     │    │   :9080     │
└─────────┘    └─────────────┘    └─────────────┘
    │                 │                 │
    │        ┌────────┼────────┐        │
    │        │        │        │        │
    ▼        ▼        ▼        ▼        ▼
┌─────────────────────────────────────────────────────────┐
│                 Core Module                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │   Entity    │  │     DTO     │  │      Tool       │  │
│  │   Classes   │  │   Classes   │  │    Classes      │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│               Persist Module                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │   MyBatis   │  │   Mapper    │  │   Database      │  │
│  │    Config   │  │ Interfaces  │  │   Scripts       │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────┘
```
### 模块功能说明

#### 🔐 Auth 模块
- **端口**: 8080（集成在Proxy模块中）
- **功能**: 提供统一的认证和鉴权服务
- **特性**: 密钥认证、IP白名单、路径白名单、调用日志记录

#### 🚀 Proxy 模块
- **端口**: 8080
- **功能**: 高性能的MCP协议代理转发
- **特性**: 基于WebFlux响应式架构、请求统计、流量监控

#### 📋 Management 模块
- **端口**: 9080
- **功能**: MCP服务和用户权限的管理界面
- **核心API**:
    - MCP服务管理：服务注册、更新、状态管理
    - 认证密钥管理：密钥申请、撤销、续期
    - 配置生成：Spring AI MCP Client配置文件生成

#### 🎯 Core 模块
- **功能**: 提供共享的实体类、DTO和工具类
- **包含**:
    - Entity: 数据库实体映射类
    - DTO: API请求/响应对象
    - Tool: 通用工具类（如密钥生成器）

#### 💾 Persist 模块
- **功能**: 数据持久化层抽象
- **技术**: MyBatis + MySQL + Redis
- **包含**: Mapper接口、数据库连接配置

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 1. 克隆项目

```bash
git clone <repository-url>
cd mcp-gateway
```

### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE mcp_gateway CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入表结构
mysql -u root -p mcp_gateway < ddl.sql
```

### 3. 配置文件

修改各模块的`application.yml`配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mcp_gateway
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 4. 编译构建

```bash
# 编译整个项目
mvn clean compile

# 打包
mvn clean package
```

### 5. 启动服务

```bash
# 启动代理模块
java -jar proxy/target/proxy-0.0.1-SNAPSHOT.jar

# 启动管理模块
java -jar management/target/management-0.0.1-SNAPSHOT.jar
```

### 6. 验证服务

```bash
# 检查代理服务
curl http://localhost:8080/mcp/health

# 检查管理服务
curl http://localhost:9080/api/management/services
```

## 使用指南

### 1. 注册MCP服务

```bash
curl -X POST http://localhost:9080/api/management/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "weather-service",
    "name": "天气查询服务",
    "description": "提供全球天气查询功能",
    "endpoint": "http://localhost:8081",
    "status": "ACTIVE",
    "maxQps": 1000
  }'
```

### 2. 申请认证密钥

```bash
curl -X POST http://localhost:9080/api/management/auth-keys/apply \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user001",
    "serviceId": "weather-service",
    "remarks": "开发测试使用"
  }'
```

### 3. 生成客户端配置

```bash
curl -X POST http://localhost:9080/api/management/config/yaml \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user001",
    "serviceIds": ["weather-service"],
    "baseUrl": "http://localhost:8080"
  }'
```

### 4. 代理访问MCP服务

```bash
# 使用生成的密钥访问服务
curl "http://localhost:8080/mcp/weather-service/api/weather?city=Beijing&key=YOUR_AUTH_KEY"
```

## 配置说明

### 认证配置

```yaml
jdt:
  mcp:
    auth:
      enabled: true
      authType: db  # 支持: db, staticKey
      validKeys:
        - "admin-key-jdt"
      whitelist:
        - "/health"
        - "/actuator/**"
      enableIpWhitelist: false
      allowedIps:
        - "127.0.0.1"
        - "::1"
```

### 代理配置

```yaml
jdt:
  mcp:
    proxy:
      timeout: 300s
      maxInMemorySize: 262144  # 256KB
      connectTimeout: 5s
      readTimeout: 30s
      enableStatistics: true
      enableRequestLogging: true
```

## 监控和运维

### 健康检查

```bash
# 代理服务健康检查
curl http://localhost:8080/mcp/health

# 管理服务健康检查
curl http://localhost:9080/actuator/health
```

### 服务统计

```bash
# 查看服务实时统计
curl http://localhost:8080/mcp/stats/weather-service/realtime

# 查看服务历史统计
curl http://localhost:8080/mcp/stats/weather-service
```

### 日志配置

```yaml
logging:
  level:
    org.jdt.mcp.gateway: INFO
  file:
    name: logs/gateway.log
```

## 部署建议

### Docker部署

```dockerfile
FROM openjdk:17-jdk-slim
COPY proxy/target/proxy-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 生产环境配置

1. **数据库连接池调优**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 50
         minimum-idle: 10
   ```

2. **JVM参数调优**
   ```bash
   java -Xms2g -Xmx4g -XX:+UseG1GC -jar app.jar
   ```

3. **Redis高可用**
   ```yaml
   spring:
     data:
       redis:
         cluster:
           nodes:
             - redis1:6379
             - redis2:6379
             - redis3:6379
   ```

## API文档

详细的API文档请参考各模块的README：

- [Auth模块API](auth/README.md)
- [Proxy模块API](proxy/README.md)
- [Management模块API](management/README.md)


## 故障排查

### 常见问题

1. **认证失败**
    - 检查认证密钥是否正确
    - 确认服务是否处于激活状态
    - 验证IP白名单配置

2. **代理超时**
    - 检查后端服务是否正常
    - 调整代理超时配置
    - 确认网络连通性

3. **数据库连接问题**
    - 检查连接池配置
    - 验证数据库权限
    - 确认防火墙设置

## RoadMap

1. 简化management和proxy繁琐的代码
2. 更完善的指标采集功能
3. 增加流量控制模块

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提 Pull Request 😄

## 版本历史

- v0.0.1-SNAPSHOT: 初始版本，包含基础的认证、代理和管理功能

## 支持

如果你在使用过程中遇到问题，可以通过以下方式获取支持：

- 提交 [GitHub Issue]
- 发送邮件至: 876989946@qq.com

---

Made with ❤️ by JDT Team