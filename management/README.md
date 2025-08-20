# 管理模块-目标
1. 提供mcp服务管理api，mcp服务信息需要持久化到mysql
2. 提供用户申请mcp服务的管理api，userid和mcp服务的对应关系需要持久化到mysql
3. mcp client配置生成功能，支持spring-ai-starter-mcp-client-webflux yml格式的配置生成，支持json格式配置生成

## 数据结构
```java
public class MCPServiceEntity {
    private Long id;
    private String serviceId;
    private String name;
    private String description;
    private String endpoint;
    private ServiceStatus status;
    private Integer maxQps;
    private String healthCheckUrl;
    private String documentation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
public class AuthKeyEntity {
    private Long id;
    private String keyHash;
    private String userId;
    private String MCPServiceId;
    private LocalDateTime expiresAt;
    @Builder.Default
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
public enum ServiceStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE,
    DEPRECATED
}
```

Client
↓ 配置文件指向
MCP Gateway (baseUrl: http://localhost:8089)
↓ 根据路径 /mcp/{serviceId} 路由到
Real MCP Services:
├── Weather Service (http://localhost:8081/weather)
├── Translation Service (http://localhost:8082/translate)  
└── File Service (http://localhost:8083/files)