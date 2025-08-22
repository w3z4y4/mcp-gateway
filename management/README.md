# 管理模块-目标
1. 提供mcp服务管理api，mcp服务信息需要持久化到mysql
2. 提供用户申请mcp服务的管理api，userid和mcp服务的对应关系需要持久化到mysql
3. mcp client配置生成功能，支持spring-ai-starter-mcp-client-webflux yml格式的配置生成，支持json格式配置生成

## 核心api
### 新增mcp服务
[MCPServiceController](management/src/main/java/org/jdt/mcp/gateway/management/ctl/MCPServiceController.java)
### 申请authKey
[AuthKeyController](management/src/main/java/org/jdt/mcp/gateway/management/ctl/AuthKeyController.java)
### 生成、预览mcp client配置（json/yml）
[ConfigGeneratorController](management/src/main/java/org/jdt/mcp/gateway/management/ctl/ConfigGeneratorController.java)

