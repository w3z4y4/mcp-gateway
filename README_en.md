# MCP Gateway - Enterprise-grade MCP API Gateway

🚀 A comprehensive solution for the MCP ecosystem, providing high-performance protocol conversion, service management, authentication, and traffic governance.

## Project Overview

MCP Gateway is an enterprise-grade API gateway system built on **Spring Boot 3.x**, designed to provide unified access, management, and proxying for **Model Context Protocol (MCP)** services. The system adopts a modular architecture to ensure scalability, maintainability, and security.

## Key Features

- 🔐 **Unified Authentication**: Supports key-based authentication, IP whitelist, and path whitelist.  
- 🚀 **High-performance Proxy**: Based on WebFlux reactive architecture, supports asynchronous request handling.  
- 📊 **Service Governance**: Service discovery, health checks, and monitoring.  
- 🛡️ **Traffic Control**: Multi-dimensional rate limiting, circuit breaking, and degradation.  
- 💾 **Data Persistence**: MySQL for configuration data, Redis for caching hot data.  
- 📈 **Real-time Monitoring**: Service invocation statistics and performance metrics.  

## Architecture

*(diagram kept unchanged, text translated where necessary)*

## Tech Stack

- **Backend Framework**: Spring Boot 3.5.4, Spring WebFlux  
- **Database**: MySQL 8.0+ (primary storage), Redis 6.0+ (cache)  
- **ORM Framework**: MyBatis  
- **Build Tool**: Maven 3.8+  
- **JDK**: Java 17+  
- **Containerization**: Docker (optional)  

## Module Structure

- `auth/` → Authentication module (auth config, filters, services, utilities)  
- `proxy/` → Proxy module (proxy config, controllers, handlers, services, schedulers)  
- `management/` → Management module (admin APIs, config, service management)  
- `core/` → Core shared module (entities, DTOs, utilities, exceptions)  
- `persist/` → Persistence module (MyBatis mappers, DB config)  

## Module Functionalities

- 🔐 **Auth Module**  
  - Port: 8080 (integrated with Proxy)  
  - Features: Key-based authentication, IP whitelist, path whitelist, request logging  

- 🚀 **Proxy Module**  
  - Port: 8080  
  - Features: High-performance MCP protocol proxying, request statistics, traffic monitoring  

- 📋 **Management Module**  
  - Port: 9080  
  - Features: MCP service and user access management  
  - Core APIs: Service registration/management, auth key lifecycle management, config generation  

- 🎯 **Core Module**  
  - Shared entities, DTOs, and utilities  

- 💾 **Persist Module**  
  - Persistence abstraction based on MyBatis + MySQL + Redis  

## Quick Start

### Prerequisites
- JDK 17+  
- Maven 3.8+  
- MySQL 8.0+  
- Redis 6.0+  

### Steps
1. Clone repository  
2. Initialize database (`ddl.sql`)  
3. Update configuration (`application.yml`)  
4. Build project with Maven  
5. Start services (`proxy` and `management` JARs)  
6. Verify endpoints (`/mcp/health`, `/api/management/services`)  

## User Guide

- Register MCP services  
- Apply for authentication keys  
- Generate client configuration  
- Access MCP services via proxy with auth keys  

## Configuration Guide

- **Authentication Config**: Supports DB-based or static key authentication, path/IP whitelist  
- **Proxy Config**: Timeout, buffer size, request logging, statistics collection  

## Monitoring & Operations

- Health checks (`/mcp/health`, `/actuator/health`)  
- Service statistics (real-time & historical)  
- Logging configuration (`logs/gateway.log`)  

## Deployment Recommendations

- **Docker** deployment sample provided  
- Production tuning for DB connection pool, JVM parameters, Redis HA setup  

## API Documentation
See individual module README files for detailed API docs.  

## Troubleshooting

Common issues include authentication failures, proxy timeouts, and DB connectivity problems. Suggested resolutions provided.  

## Roadmap
1. Simplify management and proxy codebase  
2. Enhance metric collection  
3. Add advanced traffic control module  

## Contribution Guide

1. Fork the repo  
2. Create a feature branch  
3. Commit changes  
4. Push and submit a Pull Request  

## Version History
- v0.0.1-SNAPSHOT: Initial release with basic authentication, proxy, and management features  

## Support
- Submit GitHub Issues  
- Email: 876989946@qq.com  

---

Made with ❤️ by JDT Team  
