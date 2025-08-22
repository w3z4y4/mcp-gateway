# mcp-gateway
🚀 企业级MCP协议API网关 - 为MCP生态系统提供高性能协议转换、服务管理、鉴权和流量治理的完整解决方案
## mcp-gateway-management
MCP网关管理模块，提供服务、AuthKey的增删改查，以及mcp配置生成能力
## mcp-gateway-proxy
MCP网关代理模块，提供MCP服务代理功能
## 核心api
### 新增mcp服务
[MCPServiceController](management/src/main/java/org/jdt/mcp/gateway/management/ctl/MCPServiceController.java)
### 申请authKey
[AuthKeyController](management/src/main/java/org/jdt/mcp/gateway/management/ctl/AuthKeyController.java)
### 生成、预览mcp client配置（json/yml）
[ConfigGeneratorController](management/src/main/java/org/jdt/mcp/gateway/management/ctl/ConfigGeneratorController.java)
