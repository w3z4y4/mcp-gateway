# 代理模块-目标
1. 代理mcp服务，服务列表从数据库mcp_services表中获取
2. 需要使用spring-boot-starter-webflux开发，其他模块提供了web filter鉴权
3. 代理的服务访问需要记录 service_statistics表

## 代理访问路径
Client
↓ 配置文件指向
MCP Gateway (baseUrl: http://localhost:8089)
↓ 根据路径 /mcp/{serviceId} 路由到
Real MCP Services:
├── Weather Service (http://localhost:8081/)
├── Translation Service (http://localhost:8082/)  
└── File Service (http://localhost:8083/)