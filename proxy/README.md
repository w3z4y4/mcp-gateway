# 代理模块-目标
1. 代理mcp服务，服务列表从数据库中获取
2. 需要使用spring-boot-starter-webflux开发，其他模块提供了web filter鉴权
3. 代理的服务访问需要记录 service_statistics表
4. 长时间没有调用的mcp服务或者调用失败的服务，定时做健康检查
5. 三次健康检查失败的服务需要下线