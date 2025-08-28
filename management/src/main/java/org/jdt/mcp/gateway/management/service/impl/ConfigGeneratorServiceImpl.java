package org.jdt.mcp.gateway.management.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.config.ProxyConfig;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.jdt.mcp.gateway.core.entity.MCPClientConfig;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.core.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.core.dto.ConfigValidationResult;
import org.jdt.mcp.gateway.core.dto.ServiceConfigInfo;
import org.jdt.mcp.gateway.mapper.AuthKeyMapper;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.management.service.ConfigGeneratorService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConfigGeneratorServiceImpl implements ConfigGeneratorService {

    private final MCPServiceMapper serviceMapper;
    private final AuthKeyMapper authKeyMapper;
    private final ObjectMapper objectMapper;
    private final Yaml yaml;
    private final ProxyConfig proxyConfig;

    public ConfigGeneratorServiceImpl(MCPServiceMapper serviceMapper
            , AuthKeyMapper authKeyMapper
            , ProxyConfig proxyConfig) {
        this.serviceMapper = serviceMapper;
        this.authKeyMapper = authKeyMapper;
        this.objectMapper = new ObjectMapper();
        this.proxyConfig = proxyConfig;

        // 配置YAML输出格式
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);
    }

    @Override
    public Mono<String> generateYamlConfig(ConfigGenerateRequest request) {
        return buildYamlMCPClientConfig(request)
                .map(yaml::dump)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> generateJsonConfig(ConfigGenerateRequest request) {
        return getServiceConfigs(request)
                .collectList()
                .map(serviceConfigs -> {
                    Map<String, ServiceConnectionJsonConfig> connections = new HashMap<>();
                    for (ServiceConfigInfo serviceConfig : serviceConfigs) {
                        ServiceConnectionJsonConfig jsonConfig = ServiceConnectionJsonConfig.builder()
                                .url(buildServiceJsonUrl(proxyConfig.getBaseUrl(), serviceConfig))
                                .headers(buildHeaders(serviceConfig))
                                .build();

                        connections.put(serviceConfig.getServiceId(), jsonConfig);
                    }

                    try {
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(connections);
                    } catch (JsonProcessingException e) {
                        log.error("Error generating JSON config", e);
                        throw new RuntimeException("Failed to generate JSON config", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<ServiceConfigInfo> getAvailableServicesForUser(String userId) {
        return Mono.fromCallable(() -> {
                    // 获取用户有权限的服务
                    List<AuthKeyEntity> userKeys = authKeyMapper.findByUserId(userId);
                    Set<String> authorizedServiceIds = userKeys.stream()
                            .filter(key -> key.getIsActive() &&
                                    (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now())))
                            .map(AuthKeyEntity::getMCPServiceId)
                            .collect(Collectors.toSet());

                    // 获取所有激活的服务
                    List<MCPServiceEntity> activeServices = serviceMapper.findByStatus(ServiceStatus.ACTIVE);

                    return activeServices.stream()
                            .filter(service -> authorizedServiceIds.contains(service.getServiceId()))
                            .map(service -> {
                                // 获取用户对应的密钥
                                String authKey = userKeys.stream()
                                        .filter(key -> key.getMCPServiceId().equals(service.getServiceId()) &&
                                                key.getIsActive() &&
                                                (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now())))
                                        .map(AuthKeyEntity::getKeyHash)
                                        .findFirst()
                                        .orElse(null);

                                return ServiceConfigInfo.builder()
                                        .serviceId(service.getServiceId())
                                        .serviceName(service.getName())
                                        .endpoint(service.getEndpoint())
                                        .authKey(authKey)
                                        .maxQps(service.getMaxQps())
                                        .isActive(service.getStatus() == ServiceStatus.ACTIVE)
                                        .description(service.getDescription())
                                        .build();
                            })
                            .collect(Collectors.toList());
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<ConfigValidationResult> validateConfigRequest(ConfigGenerateRequest request) {
        return getAvailableServicesForUser(request.getUserId())
                .collectMap(ServiceConfigInfo::getServiceId, service -> service)
                .map(serviceMap -> {
                    List<String> errors = new ArrayList<>();
                    List<String> warnings = new ArrayList<>();
                    int validServiceCount = 0;

                    for (String serviceId : request.getServiceIds()) {
                        ServiceConfigInfo service = serviceMap.get(serviceId);
                        if (service == null) {
                            errors.add("Service not found or not authorized: " + serviceId);
                        } else if (!service.getIsActive()) {
                            warnings.add("Service is not active: " + serviceId + " (" + service.getServiceName() + ")");
                        } else if (service.getAuthKey() == null) {
                            errors.add("No valid auth key found for service: " + serviceId);
                        } else {
                            validServiceCount++;
                        }
                    }

                    if (request.getServiceIds().isEmpty()) {
                        errors.add("At least one service must be selected");
                    }

                    if (request.getTimeout() != null && request.getTimeout() <= 0) {
                        errors.add("Timeout must be positive");
                    }

                    return ConfigValidationResult.builder()
                            .isValid(errors.isEmpty())
                            .errors(errors)
                            .warnings(warnings)
                            .validServiceCount(validServiceCount)
                            .totalServiceCount(request.getServiceIds().size())
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 构建YAML格式的MCP客户端配置
     */
    private Mono<Map<String, Object>> buildYamlMCPClientConfig(ConfigGenerateRequest request) {
        return getServiceConfigs(request)
                .collectList()
                .map(serviceConfigs -> {
                    Map<String, Object> yamlConfig = new LinkedHashMap<>();

                    // 构建 spring.ai.mcp.client 配置
                    Map<String, Object> springAiMcpClient = new LinkedHashMap<>();

                    // 构建 sse.connections
                    Map<String, Object> connections = new LinkedHashMap<>();
                    for (ServiceConfigInfo serviceConfig : serviceConfigs) {
                        Map<String, Object> connectionConfig = new LinkedHashMap<>();
                        connectionConfig.put("url", buildServiceUrl(proxyConfig.getBaseUrl(), serviceConfig));
                        connectionConfig.put("sse-endpoint", buildServiceSseUrl(proxyConfig.getBaseUrl(), serviceConfig));

                        // 使用 server1, server2... 作为连接名
                        String connectionKey = "server" + (connections.size() + 1);
                        connections.put(connectionKey, connectionConfig);
                    }

                    Map<String, Object> sseConfig = new LinkedHashMap<>();
                    sseConfig.put("connections", connections);
                    springAiMcpClient.put("sse", sseConfig);

                    // 添加 type
                    springAiMcpClient.put("type", "async");

                    // 添加 toolcallback
                    Map<String, Object> toolcallback = new LinkedHashMap<>();
                    toolcallback.put("enabled", request.getToolCallbackEnable() != null ? request.getToolCallbackEnable() : true);
                    springAiMcpClient.put("toolcallback", toolcallback);

                    yamlConfig.put("spring.ai.mcp.client", springAiMcpClient);

                    return yamlConfig;
                });
    }

    /**
     * 构建服务的SSE端点URL
     */
    private String buildServiceSseUrl(String baseUrl, ServiceConfigInfo serviceConfig) {
        String url = baseUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "mcp/" + serviceConfig.getServiceId() + "/sse";

        if (serviceConfig.getAuthKey() != null) {
            url += "?key=" + serviceConfig.getAuthKey();
        }

        return url;
    }



    private Flux<ServiceConfigInfo> getServiceConfigs(ConfigGenerateRequest request) {
        return validateConfigRequest(request)
                .flatMapMany(validation -> {
                    if (!validation.getIsValid()) {
                        return Flux.error(new IllegalArgumentException("Invalid config request: " +
                                String.join(", ", validation.getErrors())));
                    }

                    return getAvailableServicesForUser(request.getUserId())
                            .collectMap(ServiceConfigInfo::getServiceId, service -> service)
                            .flatMapMany(serviceMap ->
                                    Flux.fromIterable(request.getServiceIds())
                                            .map(serviceMap::get)
                                            .filter(Objects::nonNull)
                                            .filter(ServiceConfigInfo::getIsActive)
                                            .filter(service -> service.getAuthKey() != null)
                            );
                });
    }

    /**
     * 构建YAML配置中的服务URL（带key参数，用于url字段）
     */
    private String buildServiceUrl(String baseUrl, ServiceConfigInfo serviceConfig) {
        String url = baseUrl;

        if (serviceConfig.getAuthKey() != null) {
            url += "?key=" + serviceConfig.getAuthKey();
        }

        return url;
    }

    /**
     * 构建JSON配置中的服务URL（不带key参数，使用/sse后缀）
     */
    private String buildServiceJsonUrl(String baseUrl, ServiceConfigInfo serviceConfig) {
        String url = baseUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "mcp/" + serviceConfig.getServiceId() + "/sse";
        return url;
    }

    /**
     * 构建JSON配置中的请求头（包含Authorization）
     */
    private Map<String, String> buildHeaders(ServiceConfigInfo serviceConfig) {
        Map<String, String> headers = new HashMap<>();
        if (serviceConfig.getAuthKey() != null) {
            headers.put("Authorization", "Bearer " + serviceConfig.getAuthKey());
        }
        return headers;
    }

    /**
     * JSON格式的服务连接配置（内部类）- 更新为新格式
     */
    @lombok.Data
    @lombok.Builder
    private static class ServiceConnectionJsonConfig {
        private String url;
        private Map<String, String> headers;
    }
}